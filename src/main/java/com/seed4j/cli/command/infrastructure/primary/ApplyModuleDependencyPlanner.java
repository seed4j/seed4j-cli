package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.module.domain.landscape.Seed4JLandscapeDependency;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import com.seed4j.module.domain.resource.Seed4JModulesResources;
import com.seed4j.project.domain.ModuleSlug;
import com.seed4j.project.domain.history.ProjectAction;
import com.seed4j.project.domain.history.ProjectHistory;
import java.util.ArrayList;
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
    List<ApplyModuleDependencyPlanLine> lines = new ArrayList<>();
    Set<String> plannedDependencies = new LinkedHashSet<>();

    appendDependencies(module, modulesBySlug, visibleModules, appliedModules, lines, plannedDependencies, new LinkedHashSet<>());

    return new ApplyModuleDependencyPlan(List.copyOf(lines));
  }

  private static Set<String> appliedModules(ProjectHistory history) {
    return history.actions().stream().map(ProjectAction::module).map(ModuleSlug::get).collect(Collectors.toUnmodifiableSet());
  }

  private static Comparator<Seed4JModuleResource> byModuleSlug() {
    return Comparator.comparing(module -> module.slug().get());
  }

  private static void appendDependencies(
    Seed4JModuleResource module,
    Map<String, Seed4JModuleResource> modulesBySlug,
    List<Seed4JModuleResource> visibleModules,
    Set<String> appliedModules,
    List<ApplyModuleDependencyPlanLine> lines,
    Set<String> plannedDependencies,
    Set<String> visitedModules
  ) {
    if (!visitedModules.add(module.slug().get())) {
      return;
    }

    module
      .organization()
      .dependencies()
      .stream()
      .sorted(byDependencyToken())
      .forEach(dependency ->
        appendDependency(dependency, modulesBySlug, visibleModules, appliedModules, lines, plannedDependencies, visitedModules)
      );
  }

  private static Comparator<Seed4JLandscapeDependency> byDependencyToken() {
    return Comparator.comparing(ApplyModuleDependencyPlanner::dependencyToken);
  }

  private static void appendDependency(
    Seed4JLandscapeDependency dependency,
    Map<String, Seed4JModuleResource> modulesBySlug,
    List<Seed4JModuleResource> visibleModules,
    Set<String> appliedModules,
    List<ApplyModuleDependencyPlanLine> lines,
    Set<String> plannedDependencies,
    Set<String> visitedModules
  ) {
    switch (dependency.type()) {
      case MODULE -> appendModuleDependency(
        dependency,
        modulesBySlug,
        visibleModules,
        appliedModules,
        lines,
        plannedDependencies,
        visitedModules
      );
      case FEATURE -> appendFeatureDependency(dependency, visibleModules, appliedModules, lines, plannedDependencies);
    }
  }

  private static void appendModuleDependency(
    Seed4JLandscapeDependency dependency,
    Map<String, Seed4JModuleResource> modulesBySlug,
    List<Seed4JModuleResource> visibleModules,
    Set<String> appliedModules,
    List<ApplyModuleDependencyPlanLine> lines,
    Set<String> plannedDependencies,
    Set<String> visitedModules
  ) {
    String dependencySlug = dependency.slug().get();
    String token = dependencyToken(dependency);
    if (plannedDependencies.add(token)) {
      lines.add(new ApplyModuleDependencyPlanLine(token, moduleStatus(dependencySlug, appliedModules)));
    }

    Optional.ofNullable(modulesBySlug.get(dependencySlug)).ifPresent(resource ->
      appendDependencies(resource, modulesBySlug, visibleModules, appliedModules, lines, plannedDependencies, visitedModules)
    );
  }

  private static String moduleStatus(String dependencySlug, Set<String> appliedModules) {
    if (appliedModules.contains(dependencySlug)) {
      return "already applied";
    }

    return "pending";
  }

  private static void appendFeatureDependency(
    Seed4JLandscapeDependency dependency,
    List<Seed4JModuleResource> visibleModules,
    Set<String> appliedModules,
    List<ApplyModuleDependencyPlanLine> lines,
    Set<String> plannedDependencies
  ) {
    String token = dependencyToken(dependency);
    if (!plannedDependencies.add(token)) {
      return;
    }

    List<String> candidates = featureCandidates(dependency.slug().get(), visibleModules);
    Optional<String> appliedCandidate = candidates.stream().filter(appliedModules::contains).findFirst();
    String status = appliedCandidate.map(candidate -> "satisfied by " + candidate).orElseGet(() -> pendingChoiceStatus(candidates));

    lines.add(new ApplyModuleDependencyPlanLine(token, status));
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
}
