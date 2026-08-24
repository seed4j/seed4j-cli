package com.seed4j.cli.command.domain.moduleset;

import java.util.Collections;
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
    Map<ModuleSetDependency, Set<ModuleSetSlug>> requirements = Map.of();
    ModuleSetExecutionPositions executionPositions = ModuleSetExecutionPositions.from(executionOrder);
    for (ModuleSetSlug requestedModule : executionOrder) {
      requirements = DependencyDiscovery.startingWith(requirements, modulesBySlug, requestedModule)
        .discover(requestedModule)
        .requirements();
    }

    DependencyResolver resolver = new DependencyResolver(executionPositions, modulesBySlug, history);
    return requirements
      .entrySet()
      .stream()
      .sorted(Map.Entry.comparingByKey())
      .map(entry -> resolver.validate(entry.getKey(), entry.getValue()))
      .toList();
  }

  private record DependencyDiscovery(
    Map<ModuleSetDependency, Set<ModuleSetSlug>> requirements,
    Set<ModuleSetSlug> visitedModules,
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug,
    ModuleSetSlug requestedBy
  ) {
    private DependencyDiscovery {
      requirements = immutableRequirements(requirements);
      visitedModules = Collections.unmodifiableSet(new LinkedHashSet<>(visitedModules));
      modulesBySlug = Map.copyOf(modulesBySlug);
    }

    private static Map<ModuleSetDependency, Set<ModuleSetSlug>> immutableRequirements(
      Map<ModuleSetDependency, Set<ModuleSetSlug>> requirements
    ) {
      Map<ModuleSetDependency, Set<ModuleSetSlug>> copy = new LinkedHashMap<>();
      requirements.forEach((dependency, requiredBy) -> copy.put(dependency, Collections.unmodifiableSet(new LinkedHashSet<>(requiredBy))));
      return Collections.unmodifiableMap(copy);
    }

    private static DependencyDiscovery startingWith(
      Map<ModuleSetDependency, Set<ModuleSetSlug>> requirements,
      Map<ModuleSetSlug, ModuleSetModule> modulesBySlug,
      ModuleSetSlug requestedBy
    ) {
      return new DependencyDiscovery(requirements, Set.of(), modulesBySlug, requestedBy);
    }

    private DependencyDiscovery discover(ModuleSetSlug moduleSlug) {
      if (visited(moduleSlug)) {
        return this;
      }
      DependencyDiscovery progress = visit(moduleSlug);
      ModuleSetModule module = modulesBySlug.get(moduleSlug);
      if (module == null) {
        return progress;
      }
      for (ModuleSetDependency dependency : module.dependencies()) {
        progress = progress.require(dependency);
        if (dependency.type() == ModuleSetDependencyType.MODULE) {
          progress = progress.discover(new ModuleSetSlug(dependency.value()));
        }
      }
      return progress;
    }

    private boolean visited(ModuleSetSlug moduleSlug) {
      return visitedModules.contains(moduleSlug);
    }

    private DependencyDiscovery visit(ModuleSetSlug moduleSlug) {
      Set<ModuleSetSlug> visited = new LinkedHashSet<>(visitedModules);
      visited.add(moduleSlug);
      return new DependencyDiscovery(requirements, visited, modulesBySlug, requestedBy);
    }

    private DependencyDiscovery require(ModuleSetDependency dependency) {
      Map<ModuleSetDependency, Set<ModuleSetSlug>> updatedRequirements = new LinkedHashMap<>(requirements);
      Set<ModuleSetSlug> requiredBy = new LinkedHashSet<>(updatedRequirements.getOrDefault(dependency, Set.of()));
      requiredBy.add(requestedBy);
      updatedRequirements.put(dependency, requiredBy);
      return new DependencyDiscovery(updatedRequirements, visitedModules, modulesBySlug, requestedBy);
    }
  }

  private record DependencyResolver(
    ModuleSetExecutionPositions executionPositions,
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug,
    ModuleSetPlanningHistory history
  ) {
    private DependencyResolver {
      modulesBySlug = Map.copyOf(modulesBySlug);
    }

    private ModuleSetDependencyValidation validate(ModuleSetDependency dependency, Set<ModuleSetSlug> requiredBy) {
      List<ModuleSetSlug> requiringModules = requiredBy.stream().toList();
      List<ModuleSetSlug> candidates = candidates(dependency);
      Optional<ModuleSetSlug> historyProvider = historyProvider(dependency, candidates);
      if (historyProvider.isPresent()) {
        return new ModuleSetDependencyValidation(
          dependency,
          ModuleSetDependencyStatus.SATISFIED_BY_HISTORY,
          historyProvider,
          candidates,
          requiringModules
        );
      }

      Optional<ModuleSetSlug> requestedProvider = requestedProvider(candidates, requiringModules);
      return new ModuleSetDependencyValidation(
        dependency,
        requestedProvider.isPresent() ? ModuleSetDependencyStatus.SATISFIED_BY_REQUESTED_MODULE : ModuleSetDependencyStatus.MISSING,
        requestedProvider,
        candidates,
        requiringModules
      );
    }

    private List<ModuleSetSlug> candidates(ModuleSetDependency dependency) {
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

    private Optional<ModuleSetSlug> historyProvider(ModuleSetDependency dependency, List<ModuleSetSlug> candidates) {
      if (dependency.type() == ModuleSetDependencyType.MODULE) {
        ModuleSetSlug dependencyModule = new ModuleSetSlug(dependency.value());
        return history.appliedModules().contains(dependencyModule) ? Optional.of(dependencyModule) : Optional.empty();
      }
      return candidates.stream().filter(history.appliedModules()::contains).findFirst();
    }

    private Optional<ModuleSetSlug> requestedProvider(List<ModuleSetSlug> candidates, List<ModuleSetSlug> requiringModules) {
      return candidates
        .stream()
        .filter(candidate -> executionPositions.precedesAll(candidate, requiringModules))
        .findFirst();
    }
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
        .allMatch(requiredPosition -> candidatePosition < requiredPosition);
    }
  }
}
