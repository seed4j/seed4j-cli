package com.seed4j.cli.command.domain.moduleset;

import java.util.*;

final class ModuleSetParameterPlanner {

  ParameterPlanning plan(
    List<ModuleSetSlug> executionOrder,
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug,
    ModuleSetPlanningRequest request,
    ModuleSetPlanningHistory history
  ) {
    Map<ModuleSetPropertyKey, List<ModuleSetPropertyDefinition>> definitionsByKey = new LinkedHashMap<>();
    executionOrder
      .stream()
      .map(modulesBySlug::get)
      .flatMap(module -> module.properties().stream())
      .forEach(definition -> definitionsByKey.computeIfAbsent(definition.key(), ignored -> new ArrayList<>()).add(definition));
    List<ResolvedModuleSetParameter> resolvedParameters = new ArrayList<>();
    List<MissingRequiredModuleSetParameter> missingRequiredParameters = new ArrayList<>();
    List<ModuleSetPropertyConflict> propertyConflicts = new ArrayList<>();
    for (List<ModuleSetPropertyDefinition> definitions : definitionsByKey.values()) {
      PropertyReconciliation reconciliation = reconcile(definitions);
      ModuleSetPropertyDefinition definition = reconciliation.definition();
      propertyConflicts.addAll(reconciliation.conflicts());
      ModuleSetPropertyKey key = definition.key();
      if (request.explicitParameters().values().containsKey(key)) {
        resolvedParameters.add(
          new ResolvedModuleSetParameter(
            key,
            request.explicitParameters().values().get(key),
            ModuleSetPropertySource.EXPLICIT_INPUT,
            definition
          )
        );
      } else if (history.parameters().containsKey(key)) {
        resolvedParameters.add(
          new ResolvedModuleSetParameter(key, history.parameters().get(key), ModuleSetPropertySource.PROJECT_HISTORY, definition)
        );
      } else if (definition.mandatory()) {
        missingRequiredParameters.add(new MissingRequiredModuleSetParameter(key));
      } else {
        definition
          .defaultValue()
          .ifPresent(defaultValue ->
            resolvedParameters.add(new ResolvedModuleSetParameter(key, defaultValue.value(), ModuleSetPropertySource.DEFAULT, definition))
          );
      }
    }
    List<ModuleSetPlanningProblem> problems = new ArrayList<>();
    if (!propertyConflicts.isEmpty()) {
      problems.add(new ModuleSetPropertyConflicts(propertyConflicts));
    }
    List<ModuleSetPropertyKey> irrelevantOptions = request
      .explicitParameters()
      .values()
      .keySet()
      .stream()
      .filter(key -> !definitionsByKey.containsKey(key))
      .sorted(Comparator.comparing(ModuleSetPropertyKey::value))
      .toList();
    if (!irrelevantOptions.isEmpty()) {
      problems.add(new UnusedExplicitModuleSetParameters(irrelevantOptions));
    }
    return new ParameterPlanning(resolvedParameters, missingRequiredParameters, problems);
  }

  private static PropertyReconciliation reconcile(List<ModuleSetPropertyDefinition> definitions) {
    ModuleSetPropertyDefinition first = definitions.getFirst();
    List<ModuleSetPropertyDefaultValue> defaults = definitions
      .stream()
      .flatMap(definition -> definition.defaultValue().stream())
      .distinct()
      .sorted(Comparator.comparing(ModuleSetPropertyDefaultValue::value))
      .toList();
    List<ModuleSetPropertyDescription> descriptions = definitions
      .stream()
      .flatMap(definition -> definition.description().stream())
      .distinct()
      .sorted(Comparator.comparing(ModuleSetPropertyDescription::value))
      .toList();
    List<ModuleSetPropertyConflict> conflicts = new ArrayList<>();
    if (defaults.size() > 1) {
      conflicts.add(new ModuleSetPropertyDefaultConflict(first.key(), defaults));
    }
    if (descriptions.size() > 1) {
      conflicts.add(new ModuleSetPropertyDescriptionConflict(first.key(), descriptions));
    }
    return new PropertyReconciliation(
      new ModuleSetPropertyDefinition(
        first.key(),
        first.type(),
        definitions.stream().anyMatch(ModuleSetPropertyDefinition::mandatory)
          ? ModuleSetPropertyRequirement.REQUIRED
          : ModuleSetPropertyRequirement.OPTIONAL,
        descriptions.size() == 1 ? Optional.of(descriptions.getFirst()) : Optional.empty(),
        defaults.size() == 1 ? Optional.of(defaults.getFirst()) : Optional.empty(),
        definitions
          .stream()
          .flatMap(definition -> definition.completionCandidates().stream())
          .distinct()
          .toList()
      ),
      conflicts
    );
  }

  private record PropertyReconciliation(ModuleSetPropertyDefinition definition, List<ModuleSetPropertyConflict> conflicts) {
    private PropertyReconciliation {
      conflicts = List.copyOf(conflicts);
    }
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
