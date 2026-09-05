package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.moduleset.ExplicitModuleSetParameters;
import com.seed4j.cli.command.domain.moduleset.ModuleSetCatalog;
import com.seed4j.cli.command.domain.moduleset.ModuleSetCommitMode;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependency;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetGitState;
import com.seed4j.cli.command.domain.moduleset.ModuleSetGitStateReader;
import com.seed4j.cli.command.domain.moduleset.ModuleSetHistoryParameters;
import com.seed4j.cli.command.domain.moduleset.ModuleSetIntegerParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModule;
import com.seed4j.cli.command.domain.moduleset.ModuleSetParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistory;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistoryReader;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningRequest;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPathStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPathValidator;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefaultValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefinition;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDescription;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyKey;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyRequirement;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.ModuleSetStringParameterValue;
import com.seed4j.cli.command.domain.moduleset.RequestedModuleSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ModuleSetPlanningFixture {

  private ModuleSetPlanningFixture() {}

  static ModuleDefinition module(ModuleSetSlug slug) {
    return new ModuleDefinition(slug, List.of(), List.of(), Optional.empty());
  }

  record ModuleDefinition(
    ModuleSetSlug slug,
    List<ModuleSetDependency> dependencies,
    List<ModuleSetPropertyDefinition> properties,
    Optional<String> providedFeature
  ) {
    ModuleDefinition {
      dependencies = List.copyOf(dependencies);
      properties = List.copyOf(properties);
    }

    ModuleDefinition dependingOn(ModuleSetDependency... dependencies) {
      return new ModuleDefinition(slug, List.of(dependencies), properties, providedFeature);
    }

    ModuleDefinition withProperties(ModuleSetPropertyDefinition... properties) {
      return new ModuleDefinition(slug, dependencies, List.of(properties), providedFeature);
    }

    ModuleDefinition providing(String feature) {
      return new ModuleDefinition(slug, dependencies, properties, Optional.of(feature));
    }

    ModuleSetModule definition() {
      return new ModuleSetModule(slug, dependencies, properties, providedFeature);
    }
  }

  static PropertyDefinition property(ModuleSetPropertyKey key, ModuleSetPropertyType type) {
    return new PropertyDefinition(key, type, ModuleSetPropertyRequirement.OPTIONAL, Optional.empty(), Optional.empty());
  }

  record PropertyDefinition(
    ModuleSetPropertyKey key,
    ModuleSetPropertyType type,
    ModuleSetPropertyRequirement requirement,
    Optional<ModuleSetPropertyDescription> description,
    Optional<ModuleSetPropertyDefaultValue> defaultValue
  ) {
    PropertyDefinition required() {
      return new PropertyDefinition(key, type, ModuleSetPropertyRequirement.REQUIRED, description, defaultValue);
    }

    PropertyDefinition describedAs(String description) {
      return new PropertyDefinition(key, type, requirement, Optional.of(new ModuleSetPropertyDescription(description)), defaultValue);
    }

    PropertyDefinition withDefault(ModuleSetParameterValue value, String literal) {
      return new PropertyDefinition(key, type, requirement, description, Optional.of(new ModuleSetPropertyDefaultValue(value, literal)));
    }

    ModuleSetPropertyDefinition definition() {
      return new ModuleSetPropertyDefinition(key, type, requirement, description, defaultValue, List.of());
    }
  }

  static ModuleSetSlug slug(String value) {
    return new ModuleSetSlug(value);
  }

  static InvalidProjectPathScenario invalidProjectPathScenario() {
    ModuleSetSlug selected = slug("selected");
    return new InvalidProjectPathScenario(selected, invalidProjectPathPlanning(selected));
  }

  static InvalidProjectPathWithSelectionProblemsScenario invalidProjectPathWithSelectionProblemsScenario() {
    ModuleSetSlug selected = slug("selected");
    ModuleSetSlug unknown = slug("unknown");
    RequestedModuleSet requestedModules = new RequestedModuleSet(List.of(selected, unknown, selected));
    return new InvalidProjectPathWithSelectionProblemsScenario(selected, unknown, requestedModules, invalidProjectPathPlanning(selected));
  }

  private static PlanningScenario invalidProjectPathPlanning(ModuleSetSlug selected) {
    return planning(List.of(module(selected).definition()), List.of(selected))
      .withHistoryReader(projectPath -> {
        throw new AssertionError("History must not be read after an invalid project path");
      })
      .withProjectPathStatus(ModuleSetProjectPathStatus.NOT_DIRECTORY)
      .withGitStateReader(projectPath -> {
        throw new AssertionError("Git must not be inspected after an invalid preflight");
      });
  }

  record InvalidProjectPathScenario(ModuleSetSlug selected, PlanningScenario planning) {
    ModuleSetPlan plan() {
      return planning.planAt(Path.of("project.txt"), selected);
    }
  }

  record InvalidProjectPathWithSelectionProblemsScenario(
    ModuleSetSlug selected,
    ModuleSetSlug unknown,
    RequestedModuleSet requestedModules,
    PlanningScenario planning
  ) {
    ModuleSetPlan plan() {
      return planning.planRequestedAt(Path.of("project.txt"), requestedModules);
    }
  }

  static RecursiveModuleDependenciesScenario recursiveModuleDependenciesScenario() {
    ModuleSetSlug requested = slug("requested");
    ModuleSetSlug direct = slug("direct");
    ModuleSetSlug transitive = slug("transitive");
    ModuleSetDependency directDependency = new ModuleSetDependency(ModuleSetDependencyType.MODULE, direct.value());
    ModuleSetDependency transitiveDependency = new ModuleSetDependency(ModuleSetDependencyType.MODULE, transitive.value());
    List<ModuleSetModule> modules = List.of(
      module(requested).dependingOn(transitiveDependency, directDependency).definition(),
      module(direct).dependingOn(transitiveDependency).definition(),
      module(transitive).definition()
    );
    return new RecursiveModuleDependenciesScenario(requested, planning(modules, List.of(transitive, direct, requested)));
  }

  record RecursiveModuleDependenciesScenario(ModuleSetSlug requested, PlanningScenario planning) {
    ModuleSetPlan plan() {
      return planning.plan(requested);
    }
  }

  static ReversedPropertyTypeConflictScenario reversedPropertyTypeConflictScenario() {
    ModuleSetSlug stringModule = slug("string-module");
    ModuleSetSlug integerModule = slug("integer-module");
    ModuleSetPropertyKey shared = new ModuleSetPropertyKey("shared");
    ModuleSetPropertyDefinition stringProperty = property(shared, ModuleSetPropertyType.STRING).definition();
    ModuleSetPropertyDefinition integerProperty = property(shared, ModuleSetPropertyType.INTEGER).definition();
    List<ModuleSetModule> modules = List.of(
      module(stringModule).withProperties(stringProperty).definition(),
      module(integerModule).withProperties(integerProperty).definition()
    );
    return new ReversedPropertyTypeConflictScenario(shared, modules, List.of(stringModule, integerModule));
  }

  record ReversedPropertyTypeConflictScenario(
    ModuleSetPropertyKey shared,
    List<ModuleSetModule> modules,
    List<ModuleSetSlug> executionOrder
  ) {
    ReversedPropertyTypeConflictScenario {
      modules = List.copyOf(modules);
      executionOrder = List.copyOf(executionOrder);
    }

    ModuleSetPlan forwardPlan() {
      return plan(executionOrder);
    }

    ModuleSetPlan reversedPlan() {
      return plan(executionOrder.reversed());
    }

    private ModuleSetPlan plan(List<ModuleSetSlug> order) {
      return planning(modules, order).planRequested(new RequestedModuleSet(executionOrder));
    }
  }

  static ConflictingPropertyTypesScenario conflictingPropertyTypesScenario() {
    ModuleSetSlug first = slug("first");
    ModuleSetSlug second = slug("second");
    ConflictingPropertyTypeKeys keys = new ConflictingPropertyTypeKeys(
      new ModuleSetPropertyKey("shared"),
      new ModuleSetPropertyKey("independent"),
      new ModuleSetPropertyKey("unused")
    );
    ModuleSetPropertyDefinition stringShared = property(keys.shared(), ModuleSetPropertyType.STRING)
      .required()
      .withDefault(new ModuleSetStringParameterValue("fallback"), "fallback")
      .definition();
    ModuleSetPropertyDefinition integerShared = property(keys.shared(), ModuleSetPropertyType.INTEGER)
      .withDefault(new ModuleSetIntegerParameterValue(2), "2")
      .definition();
    ModuleSetPropertyDefinition independentProperty = property(keys.independent(), ModuleSetPropertyType.STRING).definition();
    List<ModuleSetModule> modules = List.of(
      module(first).withProperties(stringShared, independentProperty).definition(),
      module(second).withProperties(integerShared).definition()
    );
    ModuleSetHistoryParameters history = new ModuleSetHistoryParameters(
      Map.of(keys.shared(), new ModuleSetIntegerParameterValue(3)),
      List.of()
    );
    PlanningScenario planning = planning(modules, List.of(first, second)).withHistoryParameters(history);
    return new ConflictingPropertyTypesScenario(keys, new RequestedModuleSet(List.of(first, second)), planning);
  }

  record ConflictingPropertyTypeKeys(ModuleSetPropertyKey shared, ModuleSetPropertyKey independent, ModuleSetPropertyKey unused) {}

  record ConflictingPropertyTypesScenario(
    ConflictingPropertyTypeKeys keys,
    RequestedModuleSet requestedModules,
    PlanningScenario planning
  ) {
    ModuleSetPropertyKey shared() {
      return keys.shared();
    }

    ModuleSetPropertyKey independent() {
      return keys.independent();
    }

    ModuleSetPropertyKey unused() {
      return keys.unused();
    }

    ModuleSetPlan plan() {
      ExplicitModuleSetParameters parameters = new ExplicitModuleSetParameters(
        Map.of(
          shared(),
          new ModuleSetStringParameterValue("explicit"),
          independent(),
          new ModuleSetStringParameterValue("resolved"),
          unused(),
          new ModuleSetStringParameterValue("unused")
        )
      );
      return planning.planWithParameters(parameters, requestedModules);
    }
  }

  static ExplicitInputBeforeHistoryScenario explicitInputBeforeHistoryScenario() {
    ModuleSetSlug first = slug("first");
    ModuleSetSlug second = slug("second");
    ModuleSetPropertyKey packageName = new ModuleSetPropertyKey("packageName");
    ModuleSetPropertyDefinition sharedProperty = property(packageName, ModuleSetPropertyType.STRING)
      .required()
      .describedAs("Base package")
      .definition();
    List<ModuleSetModule> modules = List.of(
      module(first).withProperties(sharedProperty).definition(),
      module(second).withProperties(sharedProperty).definition()
    );
    ModuleSetHistoryParameters history = new ModuleSetHistoryParameters(
      Map.of(packageName, new ModuleSetStringParameterValue("com.history")),
      List.of()
    );
    PlanningScenario planning = planning(modules, List.of(first, second)).withHistoryParameters(history);
    return new ExplicitInputBeforeHistoryScenario(packageName, new RequestedModuleSet(List.of(first, second)), planning);
  }

  record ExplicitInputBeforeHistoryScenario(
    ModuleSetPropertyKey packageName,
    RequestedModuleSet requestedModules,
    PlanningScenario planning
  ) {
    ModuleSetPlan plan() {
      ExplicitModuleSetParameters parameters = new ExplicitModuleSetParameters(
        Map.of(packageName, new ModuleSetStringParameterValue("com.explicit"))
      );
      return planning.planWithParameters(parameters, requestedModules);
    }
  }

  static HistoryBeforeOptionalDefaultScenario historyBeforeOptionalDefaultScenario() {
    ModuleSetSlug selected = slug("selected");
    ParameterResolutionKeys keys = new ParameterResolutionKeys(
      new ModuleSetPropertyKey("historyValue"),
      new ModuleSetPropertyKey("defaultValue"),
      new ModuleSetPropertyKey("mandatoryValue")
    );
    ModuleSetPropertyDefinition historyDefinition = property(keys.history(), ModuleSetPropertyType.STRING)
      .withDefault(new ModuleSetStringParameterValue("history-default"), "history-default")
      .definition();
    ModuleSetPropertyDefinition defaultDefinition = property(keys.defaultValue(), ModuleSetPropertyType.STRING)
      .withDefault(new ModuleSetStringParameterValue("optional-default"), "optional-default")
      .definition();
    ModuleSetPropertyDefinition mandatoryDefinition = property(keys.mandatory(), ModuleSetPropertyType.STRING)
      .required()
      .withDefault(new ModuleSetStringParameterValue("informational-only"), "informational-only")
      .definition();
    ModuleSetModule selectedModule = module(selected)
      .withProperties(historyDefinition, defaultDefinition, mandatoryDefinition)
      .definition();
    ModuleSetHistoryParameters history = new ModuleSetHistoryParameters(
      Map.of(keys.history(), new ModuleSetStringParameterValue("from-history")),
      List.of()
    );
    PlanningScenario planning = planning(List.of(selectedModule), List.of(selected)).withHistoryParameters(history);
    return new HistoryBeforeOptionalDefaultScenario(keys, selected, planning);
  }

  record ParameterResolutionKeys(ModuleSetPropertyKey history, ModuleSetPropertyKey defaultValue, ModuleSetPropertyKey mandatory) {}

  record HistoryBeforeOptionalDefaultScenario(ParameterResolutionKeys keys, ModuleSetSlug selected, PlanningScenario planning) {
    ModuleSetPropertyKey historyKey() {
      return keys.history();
    }

    ModuleSetPropertyKey defaultKey() {
      return keys.defaultValue();
    }

    ModuleSetPropertyKey mandatoryKey() {
      return keys.mandatory();
    }

    ModuleSetPlan plan() {
      return planning.plan(selected);
    }
  }

  static PlanningScenario planning(List<ModuleSetModule> modules, List<ModuleSetSlug> executionOrder) {
    return planning(catalog(modules, executionOrder));
  }

  static PlanningScenario planning(ModuleSetCatalog catalog) {
    return new PlanningScenario(
      catalog,
      projectPath -> emptyHistory(),
      projectPath -> ModuleSetProjectPathStatus.VALID,
      projectPath -> ModuleSetGitState.NO_WORKTREE
    );
  }

  static ModuleSetCatalog catalog(List<ModuleSetModule> modules, List<ModuleSetSlug> executionOrder) {
    return new ModuleSetCatalog() {
      @Override
      public List<ModuleSetModule> modules() {
        return modules;
      }

      @Override
      public List<ModuleSetSlug> sort(List<ModuleSetSlug> requestedModules) {
        return executionOrder.stream().filter(requestedModules::contains).toList();
      }
    };
  }

  static ModuleSetCatalog catalogIncludingUnrequestedModules(List<ModuleSetModule> modules, List<ModuleSetSlug> executionOrder) {
    return new ModuleSetCatalog() {
      @Override
      public List<ModuleSetModule> modules() {
        return modules;
      }

      @Override
      public List<ModuleSetSlug> sort(List<ModuleSetSlug> requestedModules) {
        return executionOrder;
      }
    };
  }

  private static ModuleSetPlanningHistory emptyHistory() {
    return new ModuleSetPlanningHistory(Set.of(), new ModuleSetHistoryParameters(Map.of(), List.of()));
  }

  record PlanningScenario(
    ModuleSetCatalog catalog,
    ModuleSetPlanningHistoryReader historyReader,
    ModuleSetProjectPathValidator projectPathValidator,
    ModuleSetGitStateReader gitStateReader
  ) {
    PlanningScenario withHistory(ModuleSetPlanningHistory history) {
      return withHistoryReader(projectPath -> history);
    }

    PlanningScenario withAppliedModules(ModuleSetSlug... appliedModules) {
      return withHistory(
        new ModuleSetPlanningHistory(Set.copyOf(List.of(appliedModules)), new ModuleSetHistoryParameters(Map.of(), List.of()))
      );
    }

    PlanningScenario withHistoryParameters(ModuleSetHistoryParameters parameters) {
      return withHistory(new ModuleSetPlanningHistory(Set.of(), parameters));
    }

    PlanningScenario withHistoryReader(ModuleSetPlanningHistoryReader historyReader) {
      return new PlanningScenario(catalog, historyReader, projectPathValidator, gitStateReader);
    }

    PlanningScenario withProjectPathStatus(ModuleSetProjectPathStatus status) {
      return new PlanningScenario(catalog, historyReader, projectPath -> status, gitStateReader);
    }

    PlanningScenario withGitState(ModuleSetGitState state) {
      return withGitStateReader(projectPath -> state);
    }

    PlanningScenario withGitStateReader(ModuleSetGitStateReader gitStateReader) {
      return new PlanningScenario(catalog, historyReader, projectPathValidator, gitStateReader);
    }

    ModuleSetPlan plan(ModuleSetSlug... requestedModules) {
      return plan(RequestDetails.standard(new RequestedModuleSet(List.of(requestedModules)), ExplicitModuleSetParameters.empty()));
    }

    ModuleSetPlan planRequested(RequestedModuleSet requestedModules) {
      return plan(RequestDetails.standard(requestedModules, ExplicitModuleSetParameters.empty()));
    }

    ModuleSetPlan planWithParameters(ExplicitModuleSetParameters parameters, ModuleSetSlug... requestedModules) {
      return plan(RequestDetails.standard(new RequestedModuleSet(List.of(requestedModules)), parameters));
    }

    ModuleSetPlan planWithParameters(ExplicitModuleSetParameters parameters, RequestedModuleSet requestedModules) {
      return plan(RequestDetails.standard(requestedModules, parameters));
    }

    ModuleSetPlan planWithoutCommit(ModuleSetSlug... requestedModules) {
      return plan(RequestDetails.withoutCommit(new RequestedModuleSet(List.of(requestedModules))));
    }

    ModuleSetPlan planAt(Path projectPath, ModuleSetSlug... requestedModules) {
      return plan(RequestDetails.at(projectPath, new RequestedModuleSet(List.of(requestedModules))));
    }

    ModuleSetPlan planRequestedAt(Path projectPath, RequestedModuleSet requestedModules) {
      return plan(RequestDetails.at(projectPath, requestedModules));
    }

    private ModuleSetPlan plan(RequestDetails requestDetails) {
      ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(
        catalog,
        historyReader,
        projectPathValidator,
        gitStateReader
      );
      return service.plan(requestDetails.request());
    }
  }

  private record RequestDetails(
    RequestedModuleSet requestedModules,
    ModuleSetProjectPath projectPath,
    ExplicitModuleSetParameters parameters,
    ModuleSetCommitMode commitMode
  ) {
    private static RequestDetails standard(RequestedModuleSet requestedModules, ExplicitModuleSetParameters parameters) {
      return new RequestDetails(requestedModules, new ModuleSetProjectPath(Path.of(".")), parameters, ModuleSetCommitMode.ENABLED);
    }

    private static RequestDetails withoutCommit(RequestedModuleSet requestedModules) {
      return new RequestDetails(
        requestedModules,
        new ModuleSetProjectPath(Path.of(".")),
        ExplicitModuleSetParameters.empty(),
        ModuleSetCommitMode.DISABLED
      );
    }

    private static RequestDetails at(Path projectPath, RequestedModuleSet requestedModules) {
      return new RequestDetails(
        requestedModules,
        new ModuleSetProjectPath(projectPath),
        ExplicitModuleSetParameters.empty(),
        ModuleSetCommitMode.ENABLED
      );
    }

    private ModuleSetPlanningRequest request() {
      return new ModuleSetPlanningRequest(requestedModules, projectPath, parameters, commitMode);
    }
  }
}
