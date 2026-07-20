package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.module.domain.Seed4JSlug;
import com.seed4j.module.domain.landscape.Seed4JLandscape;
import com.seed4j.module.domain.landscape.Seed4JLandscapeDependency;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import com.seed4j.module.domain.resource.Seed4JModulesResources;
import com.seed4j.project.domain.ModuleSlug;
import com.seed4j.project.domain.history.ProjectAction;
import com.seed4j.project.domain.history.ProjectHistory;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class ApplyModuleDependencyPlanner {

  ApplyModuleDependencyPlan plan(
    Seed4JModuleResource module,
    Seed4JModulesResources resources,
    Seed4JLandscape landscape,
    ProjectHistory history
  ) {
    DependencyPlanningContext context = DependencyPlanningContext.from(resources, history);
    DependencyDiscoveryProgress progress = discoverDependencies(module, context, DependencyDiscoveryProgress.empty());
    List<ApplyModuleDependencyPlanLine> lines = orderedDependencies(progress.dependencies(), landscape)
      .stream()
      .map(dependency -> toPlanLine(dependency, context))
      .toList();

    return new ApplyModuleDependencyPlan(lines);
  }

  private static Set<String> appliedModules(ProjectHistory history) {
    return history.actions().stream().map(ProjectAction::module).map(ModuleSlug::get).collect(Collectors.toUnmodifiableSet());
  }

  private static Comparator<Seed4JModuleResource> byModuleSlug() {
    return Comparator.comparing(module -> module.slug().get());
  }

  private static DependencyDiscoveryProgress discoverDependencies(
    Seed4JModuleResource module,
    DependencyPlanningContext context,
    DependencyDiscoveryProgress progress
  ) {
    String moduleSlug = module.slug().get();
    if (progress.visitedModules().contains(moduleSlug)) {
      return progress;
    }

    return module
      .organization()
      .dependencies()
      .stream()
      .map(dependency -> dependencyDiscoveryStep(dependency, context))
      .reduce(Function.identity(), Function::andThen)
      .apply(progress.withVisitedModule(moduleSlug));
  }

  private static Function<DependencyDiscoveryProgress, DependencyDiscoveryProgress> dependencyDiscoveryStep(
    Seed4JLandscapeDependency dependency,
    DependencyPlanningContext context
  ) {
    return progress -> discoverDependency(dependency, context, progress);
  }

  private static DependencyDiscoveryProgress discoverDependency(
    Seed4JLandscapeDependency dependency,
    DependencyPlanningContext context,
    DependencyDiscoveryProgress progress
  ) {
    return switch (dependency.type()) {
      case MODULE -> discoverModuleDependency(dependency, context, progress);
      case FEATURE -> progress.withDependency(dependency);
    };
  }

  private static DependencyDiscoveryProgress discoverModuleDependency(
    Seed4JLandscapeDependency dependency,
    DependencyPlanningContext context,
    DependencyDiscoveryProgress progress
  ) {
    String dependencySlug = dependency.slug().get();
    DependencyDiscoveryProgress nextProgress = progress.withDependency(dependency);
    return context
      .module(dependencySlug)
      .map(resource -> discoverDependencies(resource, context, nextProgress))
      .orElse(nextProgress);
  }

  private static List<Seed4JLandscapeDependency> orderedDependencies(
    Set<Seed4JLandscapeDependency> dependencies,
    Seed4JLandscape landscape
  ) {
    Map<Seed4JSlug, Seed4JLandscapeDependency> dependenciesBySlug = dependencies
      .stream()
      .collect(Collectors.toMap(Seed4JLandscapeDependency::slug, Function.identity()));

    return landscape
      .levels()
      .stream()
      .flatMap(level -> level.slugs().filter(dependenciesBySlug::containsKey).sorted().map(dependenciesBySlug::get))
      .toList();
  }

  private static ApplyModuleDependencyPlanLine toPlanLine(Seed4JLandscapeDependency dependency, DependencyPlanningContext context) {
    ApplyModuleDependencyStatus status = switch (dependency.type()) {
      case MODULE -> moduleStatus(dependency.slug().get(), context.appliedModules());
      case FEATURE -> featureStatus(dependency, context);
    };

    return new ApplyModuleDependencyPlanLine(dependencyToken(dependency), status);
  }

  private static ApplyModuleDependencyStatus moduleStatus(String dependencySlug, Set<String> appliedModules) {
    if (appliedModules.contains(dependencySlug)) {
      return ApplyModuleDependencyStatus.alreadyApplied();
    }

    return ApplyModuleDependencyStatus.pending();
  }

  private static ApplyModuleDependencyStatus featureStatus(Seed4JLandscapeDependency dependency, DependencyPlanningContext context) {
    List<String> candidates = featureCandidates(dependency.slug().get(), context.visibleModules());
    return candidates
      .stream()
      .filter(context.appliedModules()::contains)
      .findFirst()
      .map(ApplyModuleDependencyStatus::satisfiedBy)
      .orElseGet(() -> ApplyModuleDependencyStatus.pendingChoice(candidates));
  }

  private static List<String> featureCandidates(String featureSlug, List<Seed4JModuleResource> visibleModules) {
    return visibleModules
      .stream()
      .filter(module ->
        module
          .organization()
          .feature()
          .map(feature -> feature.get().equals(featureSlug))
          .orElse(false)
      )
      .map(module -> module.slug().get())
      .sorted()
      .toList();
  }

  private static String dependencyToken(Seed4JLandscapeDependency dependency) {
    return dependency.type().name().toLowerCase() + ":" + dependency.slug().get();
  }

  private record DependencyPlanningContext(
    Map<String, Seed4JModuleResource> modulesBySlug,
    List<Seed4JModuleResource> visibleModules,
    Set<String> appliedModules
  ) {
    private DependencyPlanningContext {
      modulesBySlug = Map.copyOf(modulesBySlug);
      visibleModules = List.copyOf(visibleModules);
      appliedModules = Set.copyOf(appliedModules);
    }

    private static DependencyPlanningContext from(Seed4JModulesResources resources, ProjectHistory history) {
      List<Seed4JModuleResource> visibleModules = resources.stream().sorted(byModuleSlug()).toList();
      return new DependencyPlanningContext(
        visibleModules.stream().collect(Collectors.toMap(resource -> resource.slug().get(), Function.identity())),
        visibleModules,
        ApplyModuleDependencyPlanner.appliedModules(history)
      );
    }

    private Optional<Seed4JModuleResource> module(String slug) {
      return Optional.ofNullable(modulesBySlug.get(slug));
    }
  }

  private record DependencyDiscoveryProgress(Set<Seed4JLandscapeDependency> dependencies, Set<String> visitedModules) {
    private DependencyDiscoveryProgress {
      dependencies = Collections.unmodifiableSet(new LinkedHashSet<>(dependencies));
      visitedModules = Collections.unmodifiableSet(new LinkedHashSet<>(visitedModules));
    }

    private static DependencyDiscoveryProgress empty() {
      return new DependencyDiscoveryProgress(Set.of(), Set.of());
    }

    private DependencyDiscoveryProgress withVisitedModule(String moduleSlug) {
      Set<String> nextVisitedModules = new LinkedHashSet<>(visitedModules);
      nextVisitedModules.add(moduleSlug);
      return new DependencyDiscoveryProgress(dependencies, nextVisitedModules);
    }

    private DependencyDiscoveryProgress withDependency(Seed4JLandscapeDependency dependency) {
      Set<Seed4JLandscapeDependency> nextDependencies = new LinkedHashSet<>(dependencies);
      nextDependencies.add(dependency);
      return new DependencyDiscoveryProgress(nextDependencies, visitedModules);
    }
  }
}
