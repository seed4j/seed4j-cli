package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModuleSetPlanner {

  private final ModuleSetCatalog catalog;
  private final ModuleSetPlanningHistoryReader historyReader;
  private final ModuleSetDependencyPlanner dependencyPlanner;

  public ModuleSetPlanner(ModuleSetCatalog catalog, ModuleSetPlanningHistoryReader historyReader) {
    Assert.notNull("catalog", catalog);
    Assert.notNull("historyReader", historyReader);
    this.catalog = catalog;
    this.historyReader = historyReader;
    dependencyPlanner = new ModuleSetDependencyPlanner();
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
    List<String> duplicateModules = duplicates(request.requestedModules().modules());
    if (!duplicateModules.isEmpty()) {
      problems.add(new ModuleSetPlanningProblem(ModuleSetPlanningProblemType.DUPLICATE_MODULES, duplicateModules));
    }

    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug = catalog
      .modules()
      .stream()
      .collect(Collectors.toMap(ModuleSetModule::slug, Function.identity()));
    Set<ModuleSetSlug> knownModules = modulesBySlug.keySet();
    List<String> unknownModules = request
      .requestedModules()
      .modules()
      .stream()
      .filter(module -> !knownModules.contains(module))
      .map(ModuleSetSlug::value)
      .distinct()
      .sorted()
      .toList();
    if (!unknownModules.isEmpty()) {
      problems.add(new ModuleSetPlanningProblem(ModuleSetPlanningProblemType.UNKNOWN_MODULES, unknownModules));
    }

    boolean requestedModulesValid = unknownModules.isEmpty() && duplicateModules.isEmpty();
    List<ModuleSetSlug> executionOrder = requestedModulesValid ? catalog.sort(request.requestedModules().modules()) : List.of();
    List<ModuleSetDependencyValidation> dependencyValidations = List.of();
    List<ResolvedModuleSetParameter> resolvedParameters = List.of();
    List<MissingRequiredModuleSetParameter> missingRequiredParameters = List.of();
    if (!executionOrder.isEmpty()) {
      ModuleSetPlanningHistory history = historyReader.history(request.projectPath());
      dependencyValidations = dependencyPlanner.plan(executionOrder, modulesBySlug, history);
      List<String> missingDependencies = dependencyValidations
        .stream()
        .filter(validation -> validation.status() == ModuleSetDependencyStatus.MISSING)
        .map(validation -> validation.dependency().token())
        .toList();
      if (!missingDependencies.isEmpty()) {
        problems.add(new ModuleSetPlanningProblem(ModuleSetPlanningProblemType.MISSING_DEPENDENCY, missingDependencies));
      }
      ParameterPlanningResult parameterPlanning = planParameters(executionOrder, modulesBySlug, request, history);
      resolvedParameters = parameterPlanning.resolvedParameters();
      missingRequiredParameters = parameterPlanning.missingRequiredParameters();
      problems.addAll(parameterPlanning.problems());
      if (!missingRequiredParameters.isEmpty()) {
        problems.add(
          new ModuleSetPlanningProblem(
            ModuleSetPlanningProblemType.MISSING_REQUIRED_PARAMETER,
            missingRequiredParameters
              .stream()
              .map(parameter -> parameter.key().value())
              .toList()
          )
        );
      }
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

  private static ParameterPlanningResult planParameters(
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
      .map(ModuleSetPlanner::cliOption)
      .sorted()
      .toList();
    if (!irrelevantOptions.isEmpty()) {
      problems.add(new ModuleSetPlanningProblem(ModuleSetPlanningProblemType.IRRELEVANT_OPTION, irrelevantOptions));
    }
    return new ParameterPlanningResult(resolvedParameters, missingRequiredParameters, problems);
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

  private record PropertyReconciliation(ModuleSetPropertyDefinition definition, List<String> conflicts) {
    private PropertyReconciliation {
      conflicts = List.copyOf(conflicts);
    }
  }

  private record ParameterPlanningResult(
    List<ResolvedModuleSetParameter> resolvedParameters,
    List<MissingRequiredModuleSetParameter> missingRequiredParameters,
    List<ModuleSetPlanningProblem> problems
  ) {
    private ParameterPlanningResult {
      resolvedParameters = List.copyOf(resolvedParameters);
      missingRequiredParameters = List.copyOf(missingRequiredParameters);
      problems = List.copyOf(problems);
    }
  }

  private static List<String> duplicates(List<ModuleSetSlug> requestedModules) {
    Set<ModuleSetSlug> seen = new HashSet<>();
    Set<String> duplicates = new LinkedHashSet<>();
    requestedModules.forEach(module -> {
      if (!seen.add(module)) {
        duplicates.add(module.value());
      }
    });
    return duplicates.stream().sorted().toList();
  }
}
