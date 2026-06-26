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
    Set<String> appliedModules = appliedModules(history);
    List<Seed4JModuleResource> visibleModules = resources.stream().sorted(byModuleSlug()).toList();
    Map<String, Seed4JModuleResource> modulesBySlug = visibleModules
      .stream()
      .collect(Collectors.toMap(resource -> resource.slug().get(), Function.identity()));
    DependencyPlanningContext context = new DependencyPlanningContext(modulesBySlug, visibleModules, appliedModules);
    DependencyPlanningProgress progress = appendDependencies(module, context, DependencyPlanningProgress.empty());

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
    if (progress.visitedModules().contains(module.slug().get())) {
      return progress;
    }

    DependencyPlanningProgress currentProgress = progress.withVisitedModule(module.slug().get());
    List<Seed4JLandscapeDependency> dependencies = module.organization().dependencies().stream().sorted(byDependencyToken()).toList();
    for (Seed4JLandscapeDependency dependency : dependencies) {
      currentProgress = appendDependency(dependency, context, currentProgress);
    }
    return currentProgress;
  }

  private static Comparator<Seed4JLandscapeDependency> byDependencyToken() {
    return Comparator.comparing(ApplyModuleDependencyPlanner::dependencyToken);
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
    String token = dependencyToken(dependency);
    DependencyPlanningProgress currentProgress = progress;
    if (!currentProgress.plannedDependencies().contains(token)) {
      currentProgress = currentProgress.withPlanLine(
        new ApplyModuleDependencyPlanLine(token, moduleStatus(dependencySlug, context.appliedModules()))
      );
    }

    Optional<Seed4JModuleResource> dependencyModule = Optional.ofNullable(context.modulesBySlug().get(dependencySlug));
    if (dependencyModule.isEmpty()) {
      return currentProgress;
    }

    return appendDependencies(dependencyModule.get(), context, currentProgress);
  }

  private static String moduleStatus(String dependencySlug, Set<String> appliedModules) {
    if (appliedModules.contains(dependencySlug)) {
      return "already applied";
    }

    return "pending";
  }

  private static DependencyPlanningProgress appendFeatureDependency(
    Seed4JLandscapeDependency dependency,
    DependencyPlanningContext context,
    DependencyPlanningProgress progress
  ) {
    String token = dependencyToken(dependency);
    if (progress.plannedDependencies().contains(token)) {
      return progress;
    }

    List<String> candidates = featureCandidates(dependency.slug().get(), context.visibleModules());
    Optional<String> appliedCandidate = candidates.stream().filter(context.appliedModules()::contains).findFirst();
    String status = appliedCandidate.map(candidate -> "satisfied by " + candidate).orElseGet(() -> pendingChoiceStatus(candidates));

    return progress.withPlanLine(new ApplyModuleDependencyPlanLine(token, status));
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

  private static String pendingChoiceStatus(List<String> candidates) {
    if (candidates.isEmpty()) {
      return "pending choice: no visible candidates";
    }

    return "pending choice: " + String.join(", ", candidates);
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

    private DependencyPlanningProgress withPlanLine(ApplyModuleDependencyPlanLine line) {
      List<ApplyModuleDependencyPlanLine> nextLines = new ArrayList<>(lines);
      Set<String> nextPlannedDependencies = new LinkedHashSet<>(plannedDependencies);
      nextLines.add(line);
      nextPlannedDependencies.add(line.dependency());
      return new DependencyPlanningProgress(nextLines, nextPlannedDependencies, visitedModules);
    }
  }
}
