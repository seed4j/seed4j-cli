package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    ModuleSetRequestValidation requestValidation = validateRequestedModules(request.requestedModules());
    List<ModuleSetSlug> executionOrder = executionOrder(request.requestedModules(), requestValidation);
    SelectedModulesPlanning selectedModulesPlanning = planSelectedModules(request, executionOrder, requestValidation.modulesBySlug());
    return selectedModulesPlanning.moduleSetPlan(request, executionOrder, requestValidation.problems());
  }

  private ModuleSetRequestValidation validateRequestedModules(RequestedModuleSet requestedModules) {
    List<ModuleSetSlug> duplicateModules = duplicates(requestedModules.modules());
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug = modulesBySlug();
    List<ModuleSetSlug> unknownModules = unknownModules(requestedModules, modulesBySlug.keySet());
    return new ModuleSetRequestValidation(modulesBySlug, requestProblems(duplicateModules, unknownModules));
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

  private List<ModuleSetSlug> executionOrder(RequestedModuleSet requestedModules, ModuleSetRequestValidation requestValidation) {
    return requestValidation.valid() ? catalog.sort(requestedModules.modules()) : List.of();
  }

  private SelectedModulesPlanning planSelectedModules(
    ModuleSetPlanningRequest request,
    List<ModuleSetSlug> executionOrder,
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug
  ) {
    if (executionOrder.isEmpty()) {
      return SelectedModulesPlanning.empty();
    }
    ModuleSetPlanningHistory history = historyReader.history(request.projectPath());
    List<ModuleSetDependencyValidation> dependencyValidations = dependencyPlanner.plan(executionOrder, modulesBySlug, history);
    List<ModuleSetModule> selectedModules = executionOrder.stream().map(modulesBySlug::get).toList();
    ModuleSetParameterPlanner.ParameterPlanning parameterPlanning = parameterPlanner.plan(
      selectedModules,
      request.explicitParameters(),
      history.parameters()
    );
    return new SelectedModulesPlanning(dependencyValidations, parameterPlanning);
  }

  private record ModuleSetRequestValidation(Map<ModuleSetSlug, ModuleSetModule> modulesBySlug, List<ModuleSetPlanningProblem> problems) {
    private ModuleSetRequestValidation {
      modulesBySlug = Map.copyOf(modulesBySlug);
      problems = List.copyOf(problems);
    }

    private boolean valid() {
      return problems.isEmpty();
    }
  }

  private record SelectedModulesPlanning(
    List<ModuleSetDependencyValidation> dependencyValidations,
    ModuleSetParameterPlanner.ParameterPlanning parameterPlanning
  ) {
    private SelectedModulesPlanning {
      dependencyValidations = List.copyOf(dependencyValidations);
    }

    private ModuleSetPlan moduleSetPlan(
      ModuleSetPlanningRequest request,
      List<ModuleSetSlug> executionOrder,
      List<ModuleSetPlanningProblem> requestProblems
    ) {
      return new ModuleSetPlan(
        request.requestedModules(),
        request.projectPath(),
        executionOrder,
        dependencyValidations,
        parameterPlanning.resolvedParameters(),
        parameterPlanning.missingRequiredParameters(),
        Stream.concat(requestProblems.stream(), parameterPlanning.problems().stream()).toList()
      );
    }

    private static SelectedModulesPlanning empty() {
      return new SelectedModulesPlanning(List.of(), new ModuleSetParameterPlanner.ParameterPlanning(List.of(), List.of(), List.of()));
    }
  }
}
