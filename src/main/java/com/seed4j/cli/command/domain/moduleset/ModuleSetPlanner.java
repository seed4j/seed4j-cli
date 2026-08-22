package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModuleSetPlanner {

  private final ModuleSetCatalog catalog;
  private final ModuleSetPlanningHistoryReader historyReader;
  private final ModuleSetDependencyPlanner dependencyPlanner;
  private final ModuleSetParameterPlanner parameterPlanner;

  public ModuleSetPlanner(ModuleSetCatalog catalog, ModuleSetPlanningHistoryReader historyReader) {
    Assert.notNull("catalog", catalog);
    Assert.notNull("historyReader", historyReader);
    this.catalog = catalog;
    this.historyReader = historyReader;
    dependencyPlanner = new ModuleSetDependencyPlanner();
    parameterPlanner = new ModuleSetParameterPlanner();
  }

  public List<ModuleSetPropertyDefinition> availableProperties() {
    Map<ModuleSetPropertyKey, List<ModuleSetPropertyDefinition>> definitions = availablePropertyDefinitions();
    return definitions.values().stream().map(this::globalDefinition).toList();
  }

  private Map<ModuleSetPropertyKey, List<ModuleSetPropertyDefinition>> availablePropertyDefinitions() {
    Map<ModuleSetPropertyKey, List<ModuleSetPropertyDefinition>> definitions = new LinkedHashMap<>();
    catalog
      .modules()
      .stream()
      .flatMap(module -> module.properties().stream())
      .forEach(definition -> definitions.computeIfAbsent(definition.key(), key -> new ArrayList<>()).add(definition));
    return definitions;
  }

  private ModuleSetPropertyDefinition globalDefinition(List<ModuleSetPropertyDefinition> definitions) {
    ModuleSetPropertyDefinition first = definitions.getFirst();
    List<String> candidates = definitions
      .stream()
      .flatMap(definition -> definition.completionCandidates().stream())
      .distinct()
      .toList();
    return new ModuleSetPropertyDefinition(
      first.key(),
      first.type(),
      definitions.stream().anyMatch(ModuleSetPropertyDefinition::mandatory)
        ? ModuleSetPropertyRequirement.REQUIRED
        : ModuleSetPropertyRequirement.OPTIONAL,
      first.description(),
      first.defaultValue(),
      candidates
    );
  }

  public ModuleSetPlan plan(ModuleSetPlanningRequest request) {
    List<ModuleSetPlanningProblem> problems = new ArrayList<>();
    List<ModuleSetSlug> duplicateModules = duplicates(request.requestedModules().modules());
    if (!duplicateModules.isEmpty()) {
      problems.add(new DuplicateRequestedModuleSetModules(duplicateModules));
    }

    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug = catalog
      .modules()
      .stream()
      .collect(Collectors.toMap(ModuleSetModule::slug, Function.identity()));
    Set<ModuleSetSlug> knownModules = modulesBySlug.keySet();
    List<ModuleSetSlug> unknownModules = request
      .requestedModules()
      .modules()
      .stream()
      .filter(module -> !knownModules.contains(module))
      .distinct()
      .sorted(Comparator.comparing(ModuleSetSlug::value))
      .toList();
    if (!unknownModules.isEmpty()) {
      problems.add(new UnknownRequestedModuleSetModules(unknownModules));
    }

    boolean requestedModulesValid = unknownModules.isEmpty() && duplicateModules.isEmpty();
    List<ModuleSetSlug> executionOrder = requestedModulesValid ? catalog.sort(request.requestedModules().modules()) : List.of();
    List<ModuleSetDependencyValidation> dependencyValidations = List.of();
    List<ResolvedModuleSetParameter> resolvedParameters = List.of();
    List<MissingRequiredModuleSetParameter> missingRequiredParameters = List.of();
    if (!executionOrder.isEmpty()) {
      ModuleSetPlanningHistory history = historyReader.history(request.projectPath());
      dependencyValidations = dependencyPlanner.plan(executionOrder, modulesBySlug, history);
      ModuleSetParameterPlanner.ParameterPlanning parameterPlanning = parameterPlanner.plan(
        executionOrder,
        modulesBySlug,
        request,
        history
      );
      resolvedParameters = parameterPlanning.resolvedParameters();
      missingRequiredParameters = parameterPlanning.missingRequiredParameters();
      problems.addAll(parameterPlanning.problems());
    }

    return new ModuleSetPlan(
      request.requestedModules(),
      request.projectPath(),
      executionOrder,
      dependencyValidations,
      resolvedParameters,
      missingRequiredParameters,
      problems
    );
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
}
