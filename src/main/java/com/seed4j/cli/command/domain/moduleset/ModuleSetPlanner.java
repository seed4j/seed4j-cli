package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModuleSetPlanner {

  private final ModuleSetCatalog catalog;
  private final ModuleSetPlanningHistoryReader historyReader;
  private final ModuleSetProjectPathValidator projectPathValidator;
  private final ModuleSetGitStateReader gitStateReader;
  private final ModuleSetDependencyPlanner dependencyPlanner;
  private final ModuleSetParameterPlanner parameterPlanner;

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
    this.projectPathValidator = projectPathValidator;
    this.gitStateReader = gitStateReader;
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
    List<ModuleSetPlanningProblem> pathProblems = projectPathProblems(request.projectPath());
    ModuleSetRequestValidation requestValidation = validateRequestedModules(request.requestedModules());
    List<ModuleSetSlug> executionOrder = executionOrder(request.requestedModules(), requestValidation);
    List<ModuleSetPlanningProblem> orderProblems = executionOrderProblems(request.requestedModules(), executionOrder, requestValidation);
    List<ModuleSetPlanningProblem> preselectionProblems = Stream.of(
      pathProblems.stream(),
      requestValidation.problems().stream(),
      orderProblems.stream()
    )
      .flatMap(Function.identity())
      .toList();
    SelectedModulesPlanning selectedModulesPlanning =
      requestValidation.valid() && orderProblems.isEmpty()
        ? planSelectedModules(request, executionOrder, requestValidation.modulesBySlug())
        : SelectedModulesPlanning.empty();
    ModuleSetPlan plan = selectedModulesPlanning.moduleSetPlan(request, executionOrder, preselectionProblems);
    return plan.withWarnings(planningWarnings(plan));
  }

  private List<ModuleSetPlanningProblem> projectPathProblems(ModuleSetProjectPath projectPath) {
    ModuleSetProjectPathStatus status = projectPathValidator.validate(projectPath);
    return status.valid() ? List.of() : List.of(new InvalidModuleSetProjectPath(status));
  }

  private List<ModuleSetPlanningWarning> planningWarnings(ModuleSetPlan plan) {
    if (!plan.valid() || plan.commitMode().disabled()) {
      return List.of();
    }
    return gitStateReader.state(plan.projectPath()) == ModuleSetGitState.DIRTY ? List.of(new DirtyModuleSetGitWorktree()) : List.of();
  }

  private static List<ModuleSetPlanningProblem> executionOrderProblems(
    RequestedModuleSet requestedModules,
    List<ModuleSetSlug> executionOrder,
    ModuleSetRequestValidation requestValidation
  ) {
    if (!requestValidation.valid()) {
      return List.of();
    }
    Set<ModuleSetSlug> requestedSlugs = Set.copyOf(requestedModules.modules());
    Set<ModuleSetSlug> orderedSlugs = Set.copyOf(executionOrder);
    if (executionOrder.size() == requestedModules.modules().size() && orderedSlugs.equals(requestedSlugs)) {
      return List.of();
    }
    return List.of(new ModuleSetExecutionOrderMismatch(requestedModules.modules(), executionOrder));
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
    return new SelectedModulesPlanning(history.appliedModules(), dependencyValidations, parameterPlanning);
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
