package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModuleSetPlanner {

  private final ModuleSetCatalog catalog;
  private final ModuleSetPlanningHistoryReader historyReader;
  private final ModuleSetDependencyPlanner dependencyPlanner;
  private final ModuleSetPreflightEnvironmentInspector environmentInspector;
  private final ModuleSetParameterPlanner parameterPlanner;
  private final ModuleSetRequestSelector requestSelector;

  public ModuleSetPlanner(
    ModuleSetCatalog catalog,
    ModuleSetPlanningHistoryReader historyReader,
    ModuleSetProjectPathValidator projectPathValidator,
    ModuleSetGitStateReader gitStateReader
  ) {
    Assert.notNull("catalog", catalog);
    Assert.notNull("historyReader", historyReader);
    Assert.notNull("projectPathValidator", projectPathValidator);
    Assert.notNull("gitStateReader", gitStateReader);
    this.catalog = catalog;
    this.historyReader = historyReader;
    dependencyPlanner = new ModuleSetDependencyPlanner();
    environmentInspector = new ModuleSetPreflightEnvironmentInspector(projectPathValidator, gitStateReader);
    parameterPlanner = new ModuleSetParameterPlanner();
    requestSelector = new ModuleSetRequestSelector(catalog);
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
    List<ModuleSetPlanningProblem> pathProblems = environmentInspector.pathProblems(request.projectPath());
    ModuleSetRequestSelector.Selection selection = requestSelector.select(request.requestedModules());
    List<ModuleSetPlanningProblem> preselectionProblems = Stream.concat(pathProblems.stream(), selection.problems().stream()).toList();
    SelectedModulesPlanning selectedModulesPlanning = selection.approved()
      ? planSelectedModules(request, selection)
      : SelectedModulesPlanning.empty();
    ModuleSetPlan plan = selectedModulesPlanning.moduleSetPlan(request, selection.executionOrder(), preselectionProblems);
    return plan.withWarnings(environmentInspector.warnings(plan));
  }

  private SelectedModulesPlanning planSelectedModules(ModuleSetPlanningRequest request, ModuleSetRequestSelector.Selection selection) {
    ModuleSetPlanningHistory history = historyReader.history(request.projectPath());
    List<ModuleSetDependencyValidation> dependencyValidations = dependencyPlanner.plan(
      selection.executionOrder(),
      selection.modulesBySlug(),
      history
    );
    ModuleSetParameterPlanner.ParameterPlanning parameterPlanning = parameterPlanner.plan(
      selection.selectedModules(),
      request.explicitParameters(),
      history.parameters()
    );
    return new SelectedModulesPlanning(history.appliedModules(), dependencyValidations, parameterPlanning);
  }

  private record SelectedModulesPlanning(
    Set<ModuleSetSlug> appliedModules,
    List<ModuleSetDependencyValidation> dependencyValidations,
    ModuleSetParameterPlanner.ParameterPlanning parameterPlanning
  ) {
    private SelectedModulesPlanning {
      appliedModules = Set.copyOf(appliedModules);
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
        executionOrder
          .stream()
          .map(slug -> new ModuleSetPlanItem(slug, applicationKind(slug)))
          .toList(),
        request.commitMode(),
        effectiveParameters(),
        dependencyValidations,
        parameterPlanning.resolvedParameters(),
        parameterPlanning.missingRequiredParameters(),
        Stream.concat(requestProblems.stream(), parameterPlanning.problems().stream()).toList(),
        List.of()
      );
    }

    private EffectiveModuleSetParameters effectiveParameters() {
      Map<ModuleSetPropertyKey, ModuleSetParameterValue> values = parameterPlanning
        .resolvedParameters()
        .stream()
        .filter(parameter -> parameter.source() != ModuleSetPropertySource.DEFAULT)
        .collect(Collectors.toMap(ResolvedModuleSetParameter::key, ResolvedModuleSetParameter::value));
      return new EffectiveModuleSetParameters(values);
    }

    private ModuleSetApplicationKind applicationKind(ModuleSetSlug slug) {
      return appliedModules.contains(slug) ? ModuleSetApplicationKind.REAPPLICATION : ModuleSetApplicationKind.APPLICATION;
    }

    private static SelectedModulesPlanning empty() {
      return new SelectedModulesPlanning(
        Set.of(),
        List.of(),
        new ModuleSetParameterPlanner.ParameterPlanning(List.of(), List.of(), List.of())
      );
    }
  }
}
