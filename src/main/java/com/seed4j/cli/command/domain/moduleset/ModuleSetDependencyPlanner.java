package com.seed4j.cli.command.domain.moduleset;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ModuleSetDependencyPlanner {

  List<ModuleSetDependencyValidation> plan(
    List<ModuleSetSlug> executionOrder,
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug,
    ModuleSetPlanningHistory history
  ) {
    Map<ModuleSetDependency, Set<ModuleSetSlug>> requirements = new LinkedHashMap<>();
    ModuleSetExecutionPositions executionPositions = ModuleSetExecutionPositions.from(executionOrder);
    for (ModuleSetSlug requestedModule : executionOrder) {
      discoverDependencies(requestedModule, requestedModule, modulesBySlug, requirements, new HashSet<>());
    }

    return requirements
      .entrySet()
      .stream()
      .sorted(Map.Entry.comparingByKey())
      .map(entry -> dependencyValidation(entry.getKey(), entry.getValue(), executionPositions, modulesBySlug, history))
      .toList();
  }

  private static void discoverDependencies(
    ModuleSetSlug moduleSlug,
    ModuleSetSlug requestedBy,
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug,
    Map<ModuleSetDependency, Set<ModuleSetSlug>> requirements,
    Set<ModuleSetSlug> visitedModules
  ) {
    if (!visitedModules.add(moduleSlug)) {
      return;
    }
    ModuleSetModule module = modulesBySlug.get(moduleSlug);
    if (module == null) {
      return;
    }
    for (ModuleSetDependency dependency : module.dependencies()) {
      requirements.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(requestedBy);
      if (dependency.type() == ModuleSetDependencyType.MODULE) {
        discoverDependencies(new ModuleSetSlug(dependency.value()), requestedBy, modulesBySlug, requirements, visitedModules);
      }
    }
  }

  private static ModuleSetDependencyValidation dependencyValidation(
    ModuleSetDependency dependency,
    Set<ModuleSetSlug> requiredBy,
    ModuleSetExecutionPositions executionPositions,
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug,
    ModuleSetPlanningHistory history
  ) {
    List<ModuleSetSlug> requiringModules = requiredBy.stream().toList();
    List<ModuleSetSlug> candidates = featureCandidates(dependency, modulesBySlug);
    Optional<ModuleSetSlug> historyProvider = historyProvider(dependency, candidates, history);
    if (historyProvider.isPresent()) {
      return new ModuleSetDependencyValidation(
        dependency,
        ModuleSetDependencyStatus.SATISFIED_BY_HISTORY,
        historyProvider,
        candidates,
        requiringModules
      );
    }

    Optional<ModuleSetSlug> requestedProvider = requestedProvider(candidates, requiringModules, executionPositions);
    return new ModuleSetDependencyValidation(
      dependency,
      requestedProvider.isPresent() ? ModuleSetDependencyStatus.SATISFIED_BY_REQUESTED_MODULE : ModuleSetDependencyStatus.MISSING,
      requestedProvider,
      candidates,
      requiringModules
    );
  }

  private static List<ModuleSetSlug> featureCandidates(ModuleSetDependency dependency, Map<ModuleSetSlug, ModuleSetModule> modulesBySlug) {
    if (dependency.type() == ModuleSetDependencyType.MODULE) {
      return List.of(new ModuleSetSlug(dependency.value()));
    }
    return modulesBySlug
      .values()
      .stream()
      .filter(module -> module.feature().filter(dependency.value()::equals).isPresent())
      .map(ModuleSetModule::slug)
      .sorted()
      .toList();
  }

  private static Optional<ModuleSetSlug> historyProvider(
    ModuleSetDependency dependency,
    List<ModuleSetSlug> candidates,
    ModuleSetPlanningHistory history
  ) {
    if (dependency.type() == ModuleSetDependencyType.MODULE) {
      ModuleSetSlug dependencyModule = new ModuleSetSlug(dependency.value());
      return history.appliedModules().contains(dependencyModule) ? Optional.of(dependencyModule) : Optional.empty();
    }
    return candidates.stream().filter(history.appliedModules()::contains).findFirst();
  }

  private static Optional<ModuleSetSlug> requestedProvider(
    List<ModuleSetSlug> candidates,
    List<ModuleSetSlug> requiringModules,
    ModuleSetExecutionPositions executionPositions
  ) {
    return candidates
      .stream()
      .filter(candidate -> executionPositions.precedesAll(candidate, requiringModules))
      .findFirst();
  }

  private record ModuleSetExecutionPositions(Map<ModuleSetSlug, Integer> positions) {
    private ModuleSetExecutionPositions {
      positions = Map.copyOf(positions);
    }

    private static ModuleSetExecutionPositions from(List<ModuleSetSlug> executionOrder) {
      Map<ModuleSetSlug, Integer> positions = new LinkedHashMap<>();
      for (int index = 0; index < executionOrder.size(); index++) {
        positions.putIfAbsent(executionOrder.get(index), index);
      }
      return new ModuleSetExecutionPositions(positions);
    }

    private boolean precedesAll(ModuleSetSlug candidate, List<ModuleSetSlug> requiringModules) {
      Integer candidatePosition = positions.get(candidate);
      if (candidatePosition == null) {
        return false;
      }

      return requiringModules
        .stream()
        .map(positions::get)
        .allMatch(requiredPosition -> requiredPosition != null && candidatePosition < requiredPosition);
    }
  }
}
