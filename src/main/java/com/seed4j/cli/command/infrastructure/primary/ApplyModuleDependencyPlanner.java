package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.module.domain.landscape.Seed4JLandscapeDependency;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import com.seed4j.module.domain.resource.Seed4JModulesResources;
import com.seed4j.project.domain.ModuleSlug;
import com.seed4j.project.domain.history.ProjectAction;
import com.seed4j.project.domain.history.ProjectHistory;
import java.util.ArrayList;
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

  ApplyModuleDependencyPlan plan(Seed4JModuleResource module, Seed4JModulesResources resources, ProjectHistory history) {
    DependencyPlanningProgress progress = appendDependencies(
      module,
      DependencyPlanningContext.from(resources, history),
      DependencyPlanningProgress.empty()
    );

    return new ApplyModuleDependencyPlan(progress.lines());
  }

  private static Set<String> appliedModules(ProjectHistory history) {
    return history.actions().stream().map(ProjectAction::module).map(ModuleSlug::get).collect(Collectors.toUnmodifiableSet());
  }

  private static Comparator<Seed4JModuleResource> byModuleSlug() {
    return Comparator.comparing(module -> module.slug().get());
  }

  private static DependencyPlanningProgress appendDependencies(
    Seed4JModuleResource module,
    DependencyPlanningContext context,
    DependencyPlanningProgress progress
  ) {
    String moduleSlug = module.slug().get();
    if (progress.visitedModules().contains(moduleSlug)) {
      return progress;
    }

    return module
      .organization()
      .dependencies()
      .stream()
      .sorted(byDependencyToken())
      .map(dependency -> dependencyPlanningStep(dependency, context))
      .reduce(Function.identity(), Function::andThen)
      .apply(progress.withVisitedModule(moduleSlug));
  }

  private static Comparator<Seed4JLandscapeDependency> byDependencyToken() {
    return Comparator.comparing(ApplyModuleDependencyPlanner::dependencyToken);
  }

  private static Function<DependencyPlanningProgress, DependencyPlanningProgress> dependencyPlanningStep(
    Seed4JLandscapeDependency dependency,
    DependencyPlanningContext context
  ) {
    return progress -> appendDependency(dependency, context, progress);
  }

  private static DependencyPlanningProgress appendDependency(
    Seed4JLandscapeDependency dependency,
    DependencyPlanningContext context,
    DependencyPlanningProgress progress
  ) {
    return switch (dependency.type()) {
      case MODULE -> appendModuleDependency(dependency, context, progress);
      case FEATURE -> appendFeatureDependency(dependency, context, progress);
    };
  }

  private static DependencyPlanningProgress appendModuleDependency(
    Seed4JLandscapeDependency dependency,
    DependencyPlanningContext context,
    DependencyPlanningProgress progress
  ) {
    String dependencySlug = dependency.slug().get();
    DependencyPlanningProgress nextProgress = progress.withPlanLineIfMissing(
      new ApplyModuleDependencyPlanLine(dependencyToken(dependency), moduleStatus(dependencySlug, context.appliedModules()))
    );

    return context
      .module(dependencySlug)
      .map(resource -> appendDependencies(resource, context, nextProgress))
      .orElse(nextProgress);
  }

  private static ApplyModuleDependencyStatus moduleStatus(String dependencySlug, Set<String> appliedModules) {
    if (appliedModules.contains(dependencySlug)) {
      return ApplyModuleDependencyStatus.alreadyApplied();
    }

    return ApplyModuleDependencyStatus.pending();
  }

  private static DependencyPlanningProgress appendFeatureDependency(
    Seed4JLandscapeDependency dependency,
    DependencyPlanningContext context,
    DependencyPlanningProgress progress
  ) {
    return progress.withPlanLineIfMissing(
      new ApplyModuleDependencyPlanLine(dependencyToken(dependency), featureStatus(dependency, context))
    );
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

  private record DependencyPlanningProgress(
    List<ApplyModuleDependencyPlanLine> lines,
    Set<String> plannedDependencies,
    Set<String> visitedModules
  ) {
    private DependencyPlanningProgress {
      lines = List.copyOf(lines);
      plannedDependencies = Collections.unmodifiableSet(new LinkedHashSet<>(plannedDependencies));
      visitedModules = Collections.unmodifiableSet(new LinkedHashSet<>(visitedModules));
    }

    private static DependencyPlanningProgress empty() {
      return new DependencyPlanningProgress(List.of(), Set.of(), Set.of());
    }

    private DependencyPlanningProgress withVisitedModule(String moduleSlug) {
      Set<String> nextVisitedModules = new LinkedHashSet<>(visitedModules);
      nextVisitedModules.add(moduleSlug);
      return new DependencyPlanningProgress(lines, plannedDependencies, nextVisitedModules);
    }

    private DependencyPlanningProgress withPlanLineIfMissing(ApplyModuleDependencyPlanLine line) {
      if (plannedDependencies.contains(line.dependency())) {
        return this;
      }

      List<ApplyModuleDependencyPlanLine> nextLines = new ArrayList<>(lines);
      Set<String> nextPlannedDependencies = new LinkedHashSet<>(plannedDependencies);
      nextLines.add(line);
      nextPlannedDependencies.add(line.dependency());
      return new DependencyPlanningProgress(nextLines, nextPlannedDependencies, visitedModules);
    }
  }
}
