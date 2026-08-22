package com.seed4j.cli.command.domain.moduleset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class ModuleSetParameterPlanner {

  private final ModuleSetPropertyDefinitionReconciler definitionReconciler = new ModuleSetPropertyDefinitionReconciler();
  private final ModuleSetParameterResolver parameterResolver = new ModuleSetParameterResolver();

  ParameterPlanning plan(
    List<ModuleSetModule> selectedModules,
    ExplicitModuleSetParameters explicitParameters,
    ModuleSetHistoryParameters historyParameters
  ) {
    ModuleSetPropertyDefinitionReconciler.Reconciliation reconciliation = definitionReconciler.reconcile(selectedModules);
    List<ResolvedModuleSetParameter> resolvedParameters = new ArrayList<>();
    List<MissingRequiredModuleSetParameter> missingRequiredParameters = new ArrayList<>();
    List<ModuleSetHistoryParameterTypeMismatch> historyMismatches = new ArrayList<>();
    for (ModuleSetParameterResolution resolution : parameterResolver.resolve(
      reconciliation.definitions(),
      explicitParameters,
      historyParameters
    )) {
      switch (resolution) {
        case ModuleSetParameterResolution.Resolved resolved -> resolvedParameters.add(resolved.parameter());
        case ModuleSetParameterResolution.RequiredMissing missing -> missingRequiredParameters.add(missing.parameter());
        case ModuleSetParameterResolution.HistoryIncompatible incompatible -> historyMismatches.add(incompatible.mismatch());
        case ModuleSetParameterResolution.OptionalWithoutValue _ -> {
        }
      }
    }
    List<ModuleSetPlanningProblem> problems = planningProblems(reconciliation, historyMismatches, explicitParameters);
    return new ParameterPlanning(resolvedParameters, missingRequiredParameters, problems);
  }

  private static List<ModuleSetPlanningProblem> planningProblems(
    ModuleSetPropertyDefinitionReconciler.Reconciliation reconciliation,
    List<ModuleSetHistoryParameterTypeMismatch> historyMismatches,
    ExplicitModuleSetParameters explicitParameters
  ) {
    List<ModuleSetPlanningProblem> problems = new ArrayList<>();
    if (!reconciliation.conflicts().isEmpty()) {
      problems.add(new ModuleSetPropertyConflicts(reconciliation.conflicts()));
    }
    problems.addAll(
      historyMismatches
        .stream()
        .sorted(Comparator.comparing(mismatch -> mismatch.key().value()))
        .toList()
    );
    List<ModuleSetPropertyKey> unusedParameters = unusedExplicitParameters(reconciliation.definitions(), explicitParameters);
    if (!unusedParameters.isEmpty()) {
      problems.add(new UnusedExplicitModuleSetParameters(unusedParameters));
    }
    return List.copyOf(problems);
  }

  private static List<ModuleSetPropertyKey> unusedExplicitParameters(
    List<ModuleSetPropertyDefinition> definitions,
    ExplicitModuleSetParameters explicitParameters
  ) {
    Set<ModuleSetPropertyKey> definedKeys = definitions
      .stream()
      .map(ModuleSetPropertyDefinition::key)
      .collect(Collectors.toUnmodifiableSet());
    return explicitParameters
      .values()
      .keySet()
      .stream()
      .filter(key -> !definedKeys.contains(key))
      .sorted(Comparator.comparing(ModuleSetPropertyKey::value))
      .toList();
  }

  record ParameterPlanning(
    List<ResolvedModuleSetParameter> resolvedParameters,
    List<MissingRequiredModuleSetParameter> missingRequiredParameters,
    List<ModuleSetPlanningProblem> problems
  ) {
    ParameterPlanning {
      resolvedParameters = List.copyOf(resolvedParameters);
      missingRequiredParameters = List.copyOf(missingRequiredParameters);
      problems = List.copyOf(problems);
    }
  }
}
