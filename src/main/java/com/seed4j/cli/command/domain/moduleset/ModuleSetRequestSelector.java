package com.seed4j.cli.command.domain.moduleset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ModuleSetRequestSelector {

  private final ModuleSetCatalog catalog;

  ModuleSetRequestSelector(ModuleSetCatalog catalog) {
    this.catalog = catalog;
  }

  Selection select(RequestedModuleSet requestedModules) {
    List<ModuleSetSlug> duplicateModules = duplicates(requestedModules.modules());
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug = modulesBySlug();
    List<ModuleSetSlug> unknownModules = unknownModules(requestedModules, modulesBySlug.keySet());
    List<ModuleSetPlanningProblem> requestProblems = requestProblems(duplicateModules, unknownModules);
    if (!requestProblems.isEmpty()) {
      return new Selection(modulesBySlug, List.of(), requestProblems);
    }

    List<ModuleSetSlug> executionOrder = catalog.sort(requestedModules.modules());
    return new Selection(modulesBySlug, executionOrder, executionOrderProblems(requestedModules, executionOrder));
  }

  private static List<ModuleSetSlug> duplicates(List<ModuleSetSlug> requestedModules) {
    Set<ModuleSetSlug> seen = new HashSet<>();
    Set<ModuleSetSlug> duplicates = new LinkedHashSet<>();
    requestedModules.forEach(module -> {
      if (!seen.add(module)) {
        duplicates.add(module);
      }
    });
    return duplicates.stream().sorted(Comparator.comparing(ModuleSetSlug::value)).toList();
  }

  private Map<ModuleSetSlug, ModuleSetModule> modulesBySlug() {
    return catalog.modules().stream().collect(Collectors.toMap(ModuleSetModule::slug, Function.identity()));
  }

  private static List<ModuleSetSlug> unknownModules(RequestedModuleSet requestedModules, Set<ModuleSetSlug> knownModules) {
    return requestedModules
      .modules()
      .stream()
      .filter(module -> !knownModules.contains(module))
      .distinct()
      .sorted(Comparator.comparing(ModuleSetSlug::value))
      .toList();
  }

  private static List<ModuleSetPlanningProblem> requestProblems(List<ModuleSetSlug> duplicateModules, List<ModuleSetSlug> unknownModules) {
    List<ModuleSetPlanningProblem> problems = new ArrayList<>();
    if (!duplicateModules.isEmpty()) {
      problems.add(new DuplicateRequestedModuleSetModules(duplicateModules));
    }
    if (!unknownModules.isEmpty()) {
      problems.add(new UnknownRequestedModuleSetModules(unknownModules));
    }
    return List.copyOf(problems);
  }

  private static List<ModuleSetPlanningProblem> executionOrderProblems(
    RequestedModuleSet requestedModules,
    List<ModuleSetSlug> executionOrder
  ) {
    Set<ModuleSetSlug> requestedSlugs = Set.copyOf(requestedModules.modules());
    Set<ModuleSetSlug> orderedSlugs = Set.copyOf(executionOrder);
    if (executionOrder.size() == requestedModules.modules().size() && orderedSlugs.equals(requestedSlugs)) {
      return List.of();
    }
    return List.of(new ModuleSetExecutionOrderMismatch(requestedModules.modules(), executionOrder));
  }

  record Selection(
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug,
    List<ModuleSetSlug> executionOrder,
    List<ModuleSetPlanningProblem> problems
  ) {
    Selection {
      modulesBySlug = Map.copyOf(modulesBySlug);
      executionOrder = List.copyOf(executionOrder);
      problems = List.copyOf(problems);
    }

    boolean approved() {
      return problems.isEmpty();
    }

    List<ModuleSetModule> selectedModules() {
      return executionOrder.stream().map(modulesBySlug::get).toList();
    }
  }
}
