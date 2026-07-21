package com.seed4j.cli.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.domain.moduleset.ExplicitModuleSetParameters;
import com.seed4j.cli.command.domain.moduleset.ModuleSetCatalog;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependency;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModule;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistory;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningProblemType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningRequest;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefaultValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefinition;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDescription;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyKey;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyRequirement;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertySource;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.RequestedModuleSet;
import com.seed4j.cli.command.domain.moduleset.ResolvedModuleSetParameter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

@UnitTest
class ModuleSetPlanningApplicationServiceTest {

  @Test
  void shouldSatisfyModuleDependencyWithEarlierRequestedModule() {
    ModuleSetSlug init = new ModuleSetSlug("init");
    ModuleSetSlug mavenJava = new ModuleSetSlug("maven-java");
    ModuleSetModule initModule = new ModuleSetModule(init, List.of(), List.of(), java.util.Optional.empty());
    ModuleSetModule mavenJavaModule = new ModuleSetModule(
      mavenJava,
      List.of(new ModuleSetDependency(ModuleSetDependencyType.MODULE, "init")),
      List.of(),
      java.util.Optional.of("java-build-tool")
    );
    ModuleSetCatalog catalog = catalog(List.of(initModule, mavenJavaModule), List.of(init, mavenJava));
    ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(catalog, projectPath ->
      new ModuleSetPlanningHistory(Set.of(), Map.of())
    );
    ModuleSetPlanningRequest request = new ModuleSetPlanningRequest(
      new RequestedModuleSet(List.of(mavenJava, init)),
      new ModuleSetProjectPath(Path.of(".")),
      ExplicitModuleSetParameters.empty()
    );

    ModuleSetPlan plan = service.plan(request);

    assertThat(plan.executionOrder()).containsExactly(init, mavenJava);
    assertThat(plan.dependencyValidations())
      .singleElement()
      .satisfies(validation -> {
        assertThat(validation.dependency()).isEqualTo(new ModuleSetDependency(ModuleSetDependencyType.MODULE, "init"));
        assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.SATISFIED_BY_REQUESTED_MODULE);
        assertThat(validation.requiredBy()).containsExactly(mavenJava);
      });
    assertThat(plan.valid()).isTrue();
  }

  @Test
  void shouldResolveSharedPropertyOnceWithExplicitInputBeforeHistory() {
    ModuleSetSlug first = new ModuleSetSlug("first");
    ModuleSetSlug second = new ModuleSetSlug("second");
    ModuleSetPropertyKey packageName = new ModuleSetPropertyKey("packageName");
    ModuleSetPropertyDefinition sharedProperty = new ModuleSetPropertyDefinition(
      packageName,
      ModuleSetPropertyType.STRING,
      ModuleSetPropertyRequirement.REQUIRED,
      Optional.of(new ModuleSetPropertyDescription("Base package")),
      Optional.empty(),
      List.of()
    );
    ModuleSetCatalog catalog = catalog(
      List.of(
        new ModuleSetModule(first, List.of(), List.of(sharedProperty), Optional.empty()),
        new ModuleSetModule(second, List.of(), List.of(sharedProperty), Optional.empty())
      ),
      List.of(first, second)
    );
    ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(catalog, projectPath ->
      new ModuleSetPlanningHistory(Set.of(), Map.of(packageName, "com.history"))
    );
    ModuleSetPlanningRequest request = new ModuleSetPlanningRequest(
      new RequestedModuleSet(List.of(first, second)),
      new ModuleSetProjectPath(Path.of(".")),
      new ExplicitModuleSetParameters(Map.of(packageName, "com.explicit"))
    );

    ModuleSetPlan plan = service.plan(request);

    assertThat(plan.resolvedParameters())
      .singleElement()
      .satisfies(parameter -> {
        assertThat(parameter.key()).isEqualTo(packageName);
        assertThat(parameter.value()).isEqualTo("com.explicit");
        assertThat(parameter.source()).isEqualTo(ModuleSetPropertySource.EXPLICIT_CLI);
      });
    assertThat(plan.missingRequiredParameters()).isEmpty();
    assertThat(plan.valid()).isTrue();
  }

  @Test
  void shouldReportAllSharedPropertyConflictsAndMissingRequiredValue() {
    ModuleSetSlug first = new ModuleSetSlug("first");
    ModuleSetSlug second = new ModuleSetSlug("second");
    ModuleSetPropertyKey shared = new ModuleSetPropertyKey("shared");
    ModuleSetPropertyDefinition firstDefinition = new ModuleSetPropertyDefinition(
      shared,
      ModuleSetPropertyType.STRING,
      ModuleSetPropertyRequirement.REQUIRED,
      Optional.of(new ModuleSetPropertyDescription("First description")),
      Optional.of(new ModuleSetPropertyDefaultValue("first-default")),
      List.of()
    );
    ModuleSetPropertyDefinition secondDefinition = new ModuleSetPropertyDefinition(
      shared,
      ModuleSetPropertyType.STRING,
      ModuleSetPropertyRequirement.OPTIONAL,
      Optional.of(new ModuleSetPropertyDescription("Second description")),
      Optional.of(new ModuleSetPropertyDefaultValue("second-default")),
      List.of()
    );
    ModuleSetCatalog catalog = catalog(
      List.of(
        new ModuleSetModule(first, List.of(), List.of(firstDefinition), Optional.empty()),
        new ModuleSetModule(second, List.of(), List.of(secondDefinition), Optional.empty())
      ),
      List.of(first, second)
    );
    ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(catalog, projectPath ->
      new ModuleSetPlanningHistory(Set.of(), Map.of())
    );
    ModuleSetPlanningRequest request = new ModuleSetPlanningRequest(
      new RequestedModuleSet(List.of(first, second)),
      new ModuleSetProjectPath(Path.of(".")),
      ExplicitModuleSetParameters.empty()
    );

    ModuleSetPlan plan = service.plan(request);

    assertThat(plan.problems())
      .filteredOn(problem -> problem.type() == ModuleSetPlanningProblemType.PROPERTY_CONFLICT)
      .singleElement()
      .satisfies(problem ->
        assertThat(problem.values()).containsExactly(
          "shared: conflicting defaults (first-default, second-default)",
          "shared: conflicting descriptions (First description, Second description)"
        )
      );
    assertThat(plan.missingRequiredParameters())
      .extracting(parameter -> parameter.key().value())
      .containsExactly("shared");
    assertThat(plan.valid()).isFalse();
  }

  @Test
  void shouldRejectKnownPropertyOptionUnusedByRequestedModules() {
    ModuleSetSlug selected = new ModuleSetSlug("selected");
    ModuleSetSlug unselected = new ModuleSetSlug("unselected");
    ModuleSetPropertyKey unused = new ModuleSetPropertyKey("unusedProperty");
    ModuleSetPropertyDefinition unusedDefinition = new ModuleSetPropertyDefinition(
      unused,
      ModuleSetPropertyType.STRING,
      ModuleSetPropertyRequirement.OPTIONAL,
      Optional.empty(),
      Optional.empty(),
      List.of()
    );
    ModuleSetCatalog catalog = catalog(
      List.of(
        new ModuleSetModule(selected, List.of(), List.of(), Optional.empty()),
        new ModuleSetModule(unselected, List.of(), List.of(unusedDefinition), Optional.empty())
      ),
      List.of(selected, unselected)
    );
    ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(catalog, projectPath ->
      new ModuleSetPlanningHistory(Set.of(), Map.of())
    );
    ModuleSetPlanningRequest request = new ModuleSetPlanningRequest(
      new RequestedModuleSet(List.of(selected)),
      new ModuleSetProjectPath(Path.of(".")),
      new ExplicitModuleSetParameters(Map.of(unused, "value"))
    );

    ModuleSetPlan plan = service.plan(request);

    assertThat(plan.problems())
      .filteredOn(problem -> problem.type() == ModuleSetPlanningProblemType.IRRELEVANT_OPTION)
      .singleElement()
      .satisfies(problem -> assertThat(problem.values()).containsExactly("--unused-property"));
    assertThat(plan.valid()).isFalse();
  }

  @Test
  void shouldRequireExplicitVisibleFeatureProviderAndListCandidatesAlphabetically() {
    ModuleSetSlug consumer = new ModuleSetSlug("consumer");
    ModuleSetSlug maven = new ModuleSetSlug("maven-provider");
    ModuleSetSlug gradle = new ModuleSetSlug("gradle-provider");
    ModuleSetDependency buildTool = new ModuleSetDependency(ModuleSetDependencyType.FEATURE, "build-tool");
    ModuleSetCatalog catalog = catalog(
      List.of(
        new ModuleSetModule(consumer, List.of(buildTool), List.of(), Optional.empty()),
        new ModuleSetModule(maven, List.of(), List.of(), Optional.of("build-tool")),
        new ModuleSetModule(gradle, List.of(), List.of(), Optional.of("build-tool"))
      ),
      List.of(gradle, maven, consumer)
    );
    ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(catalog, projectPath ->
      new ModuleSetPlanningHistory(Set.of(), Map.of())
    );
    ModuleSetPlanningRequest request = new ModuleSetPlanningRequest(
      new RequestedModuleSet(List.of(consumer)),
      new ModuleSetProjectPath(Path.of(".")),
      ExplicitModuleSetParameters.empty()
    );

    ModuleSetPlan plan = service.plan(request);

    assertThat(plan.dependencyValidations())
      .singleElement()
      .satisfies(validation -> {
        assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.MISSING);
        assertThat(validation.candidates()).containsExactly(gradle, maven);
        assertThat(validation.provider()).isEmpty();
        assertThat(validation.requiredBy()).containsExactly(consumer);
      });
    assertThat(plan.executionOrder()).containsExactly(consumer);
    assertThat(plan.valid()).isFalse();
  }

  @Test
  void shouldSatisfyFeatureDependencyWithExplicitEarlierProvider() {
    ModuleSetSlug consumer = new ModuleSetSlug("consumer");
    ModuleSetSlug provider = new ModuleSetSlug("provider");
    ModuleSetDependency feature = new ModuleSetDependency(ModuleSetDependencyType.FEATURE, "feature");
    ModuleSetCatalog catalog = catalog(
      List.of(
        new ModuleSetModule(consumer, List.of(feature), List.of(), Optional.empty()),
        new ModuleSetModule(provider, List.of(), List.of(), Optional.of("feature"))
      ),
      List.of(provider, consumer)
    );
    ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(catalog, projectPath ->
      new ModuleSetPlanningHistory(Set.of(), Map.of())
    );
    ModuleSetPlanningRequest request = new ModuleSetPlanningRequest(
      new RequestedModuleSet(List.of(consumer, provider)),
      new ModuleSetProjectPath(Path.of(".")),
      ExplicitModuleSetParameters.empty()
    );

    ModuleSetPlan plan = service.plan(request);

    assertThat(plan.executionOrder()).containsExactly(provider, consumer);
    assertThat(plan.dependencyValidations())
      .singleElement()
      .satisfies(validation -> {
        assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.SATISFIED_BY_REQUESTED_MODULE);
        assertThat(validation.provider()).contains(provider);
      });
    assertThat(plan.valid()).isTrue();
  }

  @Test
  void shouldNotSatisfyFeatureDependencyWithProviderOrderedAfterConsumer() {
    ModuleSetSlug consumer = new ModuleSetSlug("consumer");
    ModuleSetSlug provider = new ModuleSetSlug("provider");
    ModuleSetDependency feature = new ModuleSetDependency(ModuleSetDependencyType.FEATURE, "feature");
    ModuleSetCatalog catalog = catalog(
      List.of(
        new ModuleSetModule(consumer, List.of(feature), List.of(), Optional.empty()),
        new ModuleSetModule(provider, List.of(), List.of(), Optional.of("feature"))
      ),
      List.of(consumer, provider)
    );
    ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(catalog, projectPath ->
      new ModuleSetPlanningHistory(Set.of(), Map.of())
    );
    ModuleSetPlanningRequest request = new ModuleSetPlanningRequest(
      new RequestedModuleSet(List.of(provider, consumer)),
      new ModuleSetProjectPath(Path.of(".")),
      ExplicitModuleSetParameters.empty()
    );

    ModuleSetPlan plan = service.plan(request);

    assertThat(plan.executionOrder()).containsExactly(consumer, provider);
    assertThat(plan.dependencyValidations())
      .singleElement()
      .satisfies(validation -> {
        assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.MISSING);
        assertThat(validation.provider()).isEmpty();
        assertThat(validation.candidates()).containsExactly(provider);
      });
    assertThat(plan.valid()).isFalse();
  }

  @Test
  void shouldSatisfyFeatureDependencyWithProviderFromProjectHistory() {
    ModuleSetSlug consumer = new ModuleSetSlug("consumer");
    ModuleSetSlug provider = new ModuleSetSlug("provider");
    ModuleSetDependency feature = new ModuleSetDependency(ModuleSetDependencyType.FEATURE, "feature");
    ModuleSetCatalog catalog = catalog(
      List.of(
        new ModuleSetModule(consumer, List.of(feature), List.of(), Optional.empty()),
        new ModuleSetModule(provider, List.of(), List.of(), Optional.of("feature"))
      ),
      List.of(provider, consumer)
    );
    ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(catalog, projectPath ->
      new ModuleSetPlanningHistory(Set.of(provider), Map.of())
    );
    ModuleSetPlanningRequest request = new ModuleSetPlanningRequest(
      new RequestedModuleSet(List.of(consumer)),
      new ModuleSetProjectPath(Path.of(".")),
      ExplicitModuleSetParameters.empty()
    );

    ModuleSetPlan plan = service.plan(request);

    assertThat(plan.dependencyValidations())
      .singleElement()
      .satisfies(validation -> {
        assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.SATISFIED_BY_HISTORY);
        assertThat(validation.provider()).contains(provider);
      });
    assertThat(plan.valid()).isTrue();
  }

  @Test
  void shouldPlanRecursiveDependenciesOnceAndAttributeThemToRequestedModule() {
    ModuleSetSlug requested = new ModuleSetSlug("requested");
    ModuleSetSlug direct = new ModuleSetSlug("direct");
    ModuleSetSlug transitive = new ModuleSetSlug("transitive");
    ModuleSetDependency directDependency = new ModuleSetDependency(ModuleSetDependencyType.MODULE, direct.value());
    ModuleSetDependency transitiveDependency = new ModuleSetDependency(ModuleSetDependencyType.MODULE, transitive.value());
    ModuleSetCatalog catalog = catalog(
      List.of(
        new ModuleSetModule(requested, List.of(directDependency, transitiveDependency), List.of(), Optional.empty()),
        new ModuleSetModule(direct, List.of(transitiveDependency), List.of(), Optional.empty()),
        new ModuleSetModule(transitive, List.of(), List.of(), Optional.empty())
      ),
      List.of(transitive, direct, requested)
    );
    ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(catalog, projectPath ->
      new ModuleSetPlanningHistory(Set.of(), Map.of())
    );
    ModuleSetPlanningRequest request = new ModuleSetPlanningRequest(
      new RequestedModuleSet(List.of(requested)),
      new ModuleSetProjectPath(Path.of(".")),
      ExplicitModuleSetParameters.empty()
    );

    ModuleSetPlan plan = service.plan(request);

    assertThat(plan.dependencyValidations()).hasSize(2);
    assertThat(plan.dependencyValidations())
      .extracting(validation -> validation.dependency().token())
      .containsExactly("module:direct", "module:transitive");
    assertThat(plan.dependencyValidations()).allSatisfy(validation -> {
      assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.MISSING);
      assertThat(validation.requiredBy()).containsExactly(requested);
    });
    assertThat(plan.valid()).isFalse();
  }

  @Test
  void shouldReportModuleDependencyMissingFromCatalog() {
    ModuleSetSlug consumer = new ModuleSetSlug("consumer");
    ModuleSetDependency missing = new ModuleSetDependency(ModuleSetDependencyType.MODULE, "missing-module");
    ModuleSetCatalog catalog = catalog(
      List.of(new ModuleSetModule(consumer, List.of(missing), List.of(), Optional.empty())),
      List.of(consumer)
    );
    ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(catalog, projectPath ->
      new ModuleSetPlanningHistory(Set.of(), Map.of())
    );
    ModuleSetPlanningRequest request = new ModuleSetPlanningRequest(
      new RequestedModuleSet(List.of(consumer)),
      new ModuleSetProjectPath(Path.of(".")),
      ExplicitModuleSetParameters.empty()
    );

    ModuleSetPlan plan = service.plan(request);

    assertThat(plan.dependencyValidations())
      .singleElement()
      .satisfies(validation -> {
        assertThat(validation.dependency()).isEqualTo(missing);
        assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.MISSING);
        assertThat(validation.requiredBy()).containsExactly(consumer);
      });
    assertThat(plan.problems())
      .filteredOn(problem -> problem.type() == ModuleSetPlanningProblemType.MISSING_DEPENDENCY)
      .singleElement()
      .satisfies(problem -> assertThat(problem.values()).containsExactly("module:missing-module"));
    assertThat(plan.valid()).isFalse();
  }

  @Test
  void shouldUseHistoryBeforeOptionalDefaultAndKeepMandatoryDefaultInformational() {
    ModuleSetSlug selected = new ModuleSetSlug("selected");
    ModuleSetPropertyKey historyKey = new ModuleSetPropertyKey("historyValue");
    ModuleSetPropertyKey defaultKey = new ModuleSetPropertyKey("defaultValue");
    ModuleSetPropertyKey mandatoryKey = new ModuleSetPropertyKey("mandatoryValue");
    ModuleSetPropertyDefinition historyDefinition = new ModuleSetPropertyDefinition(
      historyKey,
      ModuleSetPropertyType.STRING,
      ModuleSetPropertyRequirement.OPTIONAL,
      Optional.empty(),
      Optional.of(new ModuleSetPropertyDefaultValue("history-default")),
      List.of()
    );
    ModuleSetPropertyDefinition defaultDefinition = new ModuleSetPropertyDefinition(
      defaultKey,
      ModuleSetPropertyType.STRING,
      ModuleSetPropertyRequirement.OPTIONAL,
      Optional.empty(),
      Optional.of(new ModuleSetPropertyDefaultValue("optional-default")),
      List.of()
    );
    ModuleSetPropertyDefinition mandatoryDefinition = new ModuleSetPropertyDefinition(
      mandatoryKey,
      ModuleSetPropertyType.STRING,
      ModuleSetPropertyRequirement.REQUIRED,
      Optional.empty(),
      Optional.of(new ModuleSetPropertyDefaultValue("informational-only")),
      List.of()
    );
    ModuleSetCatalog catalog = catalog(
      List.of(
        new ModuleSetModule(selected, List.of(), List.of(historyDefinition, defaultDefinition, mandatoryDefinition), Optional.empty())
      ),
      List.of(selected)
    );
    ModuleSetPlanningApplicationService service = new ModuleSetPlanningApplicationService(catalog, projectPath ->
      new ModuleSetPlanningHistory(Set.of(), Map.of(historyKey, "from-history"))
    );
    ModuleSetPlanningRequest request = new ModuleSetPlanningRequest(
      new RequestedModuleSet(List.of(selected)),
      new ModuleSetProjectPath(Path.of(".")),
      ExplicitModuleSetParameters.empty()
    );

    ModuleSetPlan plan = service.plan(request);

    assertThat(plan.resolvedParameters()).extracting(ResolvedModuleSetParameter::value).containsExactly("from-history", "optional-default");
    assertThat(plan.resolvedParameters())
      .extracting(ResolvedModuleSetParameter::source)
      .containsExactly(ModuleSetPropertySource.PROJECT_HISTORY, ModuleSetPropertySource.DEFAULT);
    assertThat(plan.missingRequiredParameters())
      .extracting(parameter -> parameter.key().value())
      .containsExactly("mandatoryValue");
    assertThat(plan.valid()).isFalse();
  }

  private static ModuleSetCatalog catalog(List<ModuleSetModule> modules, List<ModuleSetSlug> executionOrder) {
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
}
