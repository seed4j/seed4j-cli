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
    List<ModuleSetExplicitParameterTypeMismatch> explicitTypeMismatches = explicitTypeMismatches(
      reconciliation.definitions(),
      explicitParameters
    );
    Set<ModuleSetPropertyKey> mismatchedExplicitKeys = explicitTypeMismatches
      .stream()
      .map(ModuleSetExplicitParameterTypeMismatch::key)
      .collect(Collectors.toUnmodifiableSet());
    ModuleSetParameterResolutionSummary resolutions = ModuleSetParameterResolutionSummary.from(
      parameterResolver.resolve(
        reconciliation
          .definitions()
          .stream()
          .filter(definition -> !mismatchedExplicitKeys.contains(definition.key()))
          .toList(),
        explicitParameters,
        historyParameters
      )
    );
    return new ParameterPlanning(
      resolutions.resolvedParameters(),
      resolutions.missingRequiredParameters(),
      planningProblems(reconciliation, explicitTypeMismatches, resolutions.historyMismatches(), explicitParameters)
    );
  }

  private static List<ModuleSetExplicitParameterTypeMismatch> explicitTypeMismatches(
    List<ModuleSetPropertyDefinition> definitions,
    ExplicitModuleSetParameters explicitParameters
  ) {
    return definitions
      .stream()
      .filter(definition -> explicitParameters.values().containsKey(definition.key()))
      .filter(definition -> explicitParameters.values().get(definition.key()).type() != definition.type())
      .map(definition ->
        new ModuleSetExplicitParameterTypeMismatch(
          definition.key(),
          definition.type(),
          explicitParameters.values().get(definition.key()).type()
        )
      )
      .sorted(Comparator.comparing(mismatch -> mismatch.key().value()))
      .toList();
  }

  private static List<ModuleSetPlanningProblem> planningProblems(
    ModuleSetPropertyDefinitionReconciler.Reconciliation reconciliation,
    List<ModuleSetExplicitParameterTypeMismatch> explicitTypeMismatches,
    List<ModuleSetHistoryParameterTypeMismatch> historyMismatches,
    ExplicitModuleSetParameters explicitParameters
  ) {
    List<ModuleSetPlanningProblem> problems = new ArrayList<>();
    if (!reconciliation.conflicts().isEmpty()) {
      problems.add(new ModuleSetPropertyConflicts(reconciliation.conflicts()));
    }
    problems.addAll(explicitTypeMismatches);
    problems.addAll(
      historyMismatches
        .stream()
        .sorted(Comparator.comparing(mismatch -> mismatch.key().value()))
        .toList()
    );
    List<ModuleSetPropertyKey> unusedParameters = unusedExplicitParameters(reconciliation.knownKeys(), explicitParameters);
    if (!unusedParameters.isEmpty()) {
      problems.add(new UnusedExplicitModuleSetParameters(unusedParameters));
    }
    return List.copyOf(problems);
  }

  private static List<ModuleSetPropertyKey> unusedExplicitParameters(
    Set<ModuleSetPropertyKey> definedKeys,
    ExplicitModuleSetParameters explicitParameters
  ) {
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
