package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModuleSetPlanner {

  private final ModuleSetCatalog catalog;
  private final ModuleSetPlanningHistoryReader historyReader;

  public ModuleSetPlanner(ModuleSetCatalog catalog, ModuleSetPlanningHistoryReader historyReader) {
    Assert.notNull("catalog", catalog);
    Assert.notNull("historyReader", historyReader);
    this.catalog = catalog;
    this.historyReader = historyReader;
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
      dependencyValidations = validateDependencies(executionOrder, modulesBySlug, history);
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
      ModuleSetPropertyDefinition definition = reconcile(definitions, propertyConflicts);
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

  private static ModuleSetPropertyDefinition reconcile(List<ModuleSetPropertyDefinition> definitions, List<String> propertyConflicts) {
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
    if (defaults.size() > 1) {
      propertyConflicts.add("%s: conflicting defaults (%s)".formatted(first.key().value(), String.join(", ", defaults)));
    }
    if (descriptions.size() > 1) {
      propertyConflicts.add("%s: conflicting descriptions (%s)".formatted(first.key().value(), String.join(", ", descriptions)));
    }
    return new ModuleSetPropertyDefinition(
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
    );
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

  private static List<ModuleSetDependencyValidation> validateDependencies(
    List<ModuleSetSlug> executionOrder,
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug,
    ModuleSetPlanningHistory history
  ) {
    Map<ModuleSetDependency, Set<ModuleSetSlug>> requirements = new LinkedHashMap<>();
    for (ModuleSetSlug requestedModule : executionOrder) {
      discoverDependencies(requestedModule, requestedModule, modulesBySlug, requirements, new HashSet<>());
    }

    return requirements
      .entrySet()
      .stream()
      .sorted(Map.Entry.comparingByKey())
      .map(entry -> dependencyValidation(entry.getKey(), entry.getValue(), executionOrder, modulesBySlug, history))
      .toList();
  }

  private static void discoverDependencies(
    ModuleSetSlug moduleSlug,
    ModuleSetSlug requestedBy,
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug,
    Map<ModuleSetDependency, Set<ModuleSetSlug>> requirements,
    Set<ModuleSetSlug> visitedModules
  ) {
    if (!visitedModules.add(moduleSlug)) {
      return;
    }
    ModuleSetModule module = modulesBySlug.get(moduleSlug);
    if (module == null) {
      return;
    }
    for (ModuleSetDependency dependency : module.dependencies()) {
      requirements.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(requestedBy);
      if (dependency.type() == ModuleSetDependencyType.MODULE) {
        discoverDependencies(new ModuleSetSlug(dependency.value()), requestedBy, modulesBySlug, requirements, visitedModules);
      }
    }
  }

  private static ModuleSetDependencyValidation dependencyValidation(
    ModuleSetDependency dependency,
    Set<ModuleSetSlug> requiredBy,
    List<ModuleSetSlug> executionOrder,
    Map<ModuleSetSlug, ModuleSetModule> modulesBySlug,
    ModuleSetPlanningHistory history
  ) {
    List<ModuleSetSlug> requiringModules = requiredBy.stream().toList();
    List<ModuleSetSlug> candidates = featureCandidates(dependency, modulesBySlug);
    Optional<ModuleSetSlug> historyProvider = historyProvider(dependency, candidates, history);
    if (historyProvider.isPresent()) {
      return new ModuleSetDependencyValidation(
        dependency,
        ModuleSetDependencyStatus.SATISFIED_BY_HISTORY,
        historyProvider,
        candidates,
        requiringModules
      );
    }

    Optional<ModuleSetSlug> requestedProvider = requestedProvider(dependency, candidates, requiringModules, executionOrder);
    return new ModuleSetDependencyValidation(
      dependency,
      requestedProvider.isPresent() ? ModuleSetDependencyStatus.SATISFIED_BY_REQUESTED_MODULE : ModuleSetDependencyStatus.MISSING,
      requestedProvider,
      candidates,
      requiringModules
    );
  }

  private static List<ModuleSetSlug> featureCandidates(ModuleSetDependency dependency, Map<ModuleSetSlug, ModuleSetModule> modulesBySlug) {
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

  private static Optional<ModuleSetSlug> historyProvider(
    ModuleSetDependency dependency,
    List<ModuleSetSlug> candidates,
    ModuleSetPlanningHistory history
  ) {
    if (dependency.type() == ModuleSetDependencyType.MODULE) {
      ModuleSetSlug dependencyModule = new ModuleSetSlug(dependency.value());
      return history.appliedModules().contains(dependencyModule) ? Optional.of(dependencyModule) : Optional.empty();
    }
    return candidates.stream().filter(history.appliedModules()::contains).findFirst();
  }

  private static Optional<ModuleSetSlug> requestedProvider(
    ModuleSetDependency dependency,
    List<ModuleSetSlug> candidates,
    List<ModuleSetSlug> requiringModules,
    List<ModuleSetSlug> executionOrder
  ) {
    return candidates
      .stream()
      .filter(executionOrder::contains)
      .filter(candidate ->
        requiringModules.stream().allMatch(requiredBy -> executionOrder.indexOf(candidate) < executionOrder.indexOf(requiredBy))
      )
      .findFirst();
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
