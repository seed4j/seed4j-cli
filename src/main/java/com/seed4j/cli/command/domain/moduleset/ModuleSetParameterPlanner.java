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
    List<String> propertyConflicts = new ArrayList<>();
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
            ModuleSetPropertySource.EXPLICIT_CLI,
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
      problems.add(new ModuleSetPlanningProblem(ModuleSetPlanningProblemType.PROPERTY_CONFLICT, propertyConflicts));
    }
    List<String> irrelevantOptions = request
      .explicitParameters()
      .values()
      .keySet()
      .stream()
      .filter(key -> !definitionsByKey.containsKey(key))
      .map(ModuleSetParameterPlanner::cliOption)
      .sorted()
      .toList();
    if (!irrelevantOptions.isEmpty()) {
      problems.add(new ModuleSetPlanningProblem(ModuleSetPlanningProblemType.IRRELEVANT_OPTION, irrelevantOptions));
    }
    return new ParameterPlanning(resolvedParameters, missingRequiredParameters, problems);
  }

  private static PropertyReconciliation reconcile(List<ModuleSetPropertyDefinition> definitions) {
    ModuleSetPropertyDefinition first = definitions.getFirst();
    List<String> defaults = definitions
      .stream()
      .flatMap(definition -> definition.defaultValue().stream())
      .map(ModuleSetPropertyDefaultValue::value)
      .distinct()
      .sorted()
      .toList();
    List<String> descriptions = definitions
      .stream()
      .flatMap(definition -> definition.description().stream())
      .map(ModuleSetPropertyDescription::value)
      .distinct()
      .sorted()
      .toList();
    List<String> conflicts = new ArrayList<>();
    if (defaults.size() > 1) {
      conflicts.add("%s: conflicting defaults (%s)".formatted(first.key().value(), String.join(", ", defaults)));
    }
    if (descriptions.size() > 1) {
      conflicts.add("%s: conflicting descriptions (%s)".formatted(first.key().value(), String.join(", ", descriptions)));
    }
    return new PropertyReconciliation(
      new ModuleSetPropertyDefinition(
        first.key(),
        first.type(),
        definitions.stream().anyMatch(ModuleSetPropertyDefinition::mandatory)
          ? ModuleSetPropertyRequirement.REQUIRED
          : ModuleSetPropertyRequirement.OPTIONAL,
        descriptions.size() == 1 ? Optional.of(new ModuleSetPropertyDescription(descriptions.getFirst())) : Optional.empty(),
        defaults.size() == 1 ? Optional.of(new ModuleSetPropertyDefaultValue(defaults.getFirst())) : Optional.empty(),
        definitions
          .stream()
          .flatMap(definition -> definition.completionCandidates().stream())
          .distinct()
          .toList()
      ),
      conflicts
    );
  }

  private static String cliOption(ModuleSetPropertyKey key) {
    StringBuilder option = new StringBuilder("--");
    for (char character : key.value().toCharArray()) {
      if (Character.isUpperCase(character)) {
        option.append('-').append(Character.toLowerCase(character));
      } else {
        option.append(character);
      }
    }
    return option.toString();
  }

  private record PropertyReconciliation(ModuleSetPropertyDefinition definition, List<String> conflicts) {
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
