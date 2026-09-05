package com.seed4j.cli.command.application;

import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.catalogIncludingUnrequestedModules;
import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.conflictingPropertyTypesScenario;
import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.explicitInputBeforeHistoryScenario;
import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.historyBeforeOptionalDefaultScenario;
import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.invalidProjectPathScenario;
import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.invalidProjectPathWithSelectionProblemsScenario;
import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.module;
import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.planning;
import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.property;
import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.recursiveModuleDependenciesScenario;
import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.reversedPropertyTypeConflictScenario;
import static com.seed4j.cli.command.application.ModuleSetPlanningFixture.slug;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.application.ModuleSetPlanningFixture.ConflictingPropertyTypesScenario;
import com.seed4j.cli.command.application.ModuleSetPlanningFixture.ExplicitInputBeforeHistoryScenario;
import com.seed4j.cli.command.application.ModuleSetPlanningFixture.HistoryBeforeOptionalDefaultScenario;
import com.seed4j.cli.command.application.ModuleSetPlanningFixture.InvalidProjectPathScenario;
import com.seed4j.cli.command.application.ModuleSetPlanningFixture.InvalidProjectPathWithSelectionProblemsScenario;
import com.seed4j.cli.command.application.ModuleSetPlanningFixture.RecursiveModuleDependenciesScenario;
import com.seed4j.cli.command.application.ModuleSetPlanningFixture.ReversedPropertyTypeConflictScenario;
import com.seed4j.cli.command.domain.moduleset.DirtyModuleSetGitWorktree;
import com.seed4j.cli.command.domain.moduleset.DuplicateRequestedModuleSetModules;
import com.seed4j.cli.command.domain.moduleset.ExplicitModuleSetParameters;
import com.seed4j.cli.command.domain.moduleset.IncompatibleExplicitModuleSetParameterTypeException;
import com.seed4j.cli.command.domain.moduleset.InvalidModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetApplicationKind;
import com.seed4j.cli.command.domain.moduleset.ModuleSetCommitMode;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependency;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDetailedPlanningStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionOrderMismatch;
import com.seed4j.cli.command.domain.moduleset.ModuleSetGitState;
import com.seed4j.cli.command.domain.moduleset.ModuleSetHistoryParameterTypeMismatch;
import com.seed4j.cli.command.domain.moduleset.ModuleSetHistoryParameterValueType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetHistoryParameters;
import com.seed4j.cli.command.domain.moduleset.ModuleSetIntegerParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModule;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyConflicts;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefaultConflict;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefaultValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefinition;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDescription;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDescriptionConflict;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyKey;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertySource;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyTypeConflict;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.ModuleSetStringParameterValue;
import com.seed4j.cli.command.domain.moduleset.ResolvedModuleSetParameter;
import com.seed4j.cli.command.domain.moduleset.UnknownRequestedModuleSetModules;
import com.seed4j.cli.command.domain.moduleset.UnsupportedModuleSetHistoryParameter;
import com.seed4j.cli.command.domain.moduleset.UnusedExplicitModuleSetParameters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@UnitTest
class ModuleSetPlanningApplicationServiceTest {

  @Nested
  class PreflightEnvironment {

    @Test
    void shouldWarnAndKeepPlanValidForDirtyGitWorktree() {
      ModuleSetSlug selected = slug("selected");

      ModuleSetPlan plan = planning(List.of(module(selected).definition()), List.of(selected))
        .withGitState(ModuleSetGitState.DIRTY)
        .plan(selected);

      assertThat(plan.warnings()).containsExactly(new DirtyModuleSetGitWorktree());
      assertThat(plan.valid()).isTrue();
    }

    @Test
    void shouldSkipGitInspectionWhenCommitIsDisabled() {
      ModuleSetSlug selected = slug("selected");

      ModuleSetPlan plan = planning(List.of(module(selected).definition()), List.of(selected))
        .withGitStateReader(projectPath -> {
          throw new AssertionError("Git must not be inspected when commit is disabled");
        })
        .planWithoutCommit(selected);

      assertThat(plan.commitMode()).isEqualTo(ModuleSetCommitMode.DISABLED);
      assertThat(plan.warnings()).isEmpty();
      assertThat(plan.valid()).isTrue();
    }

    @Test
    void shouldRejectInvalidProjectPathWithoutInspectingGit() {
      InvalidProjectPathScenario scenario = invalidProjectPathScenario();

      ModuleSetPlan plan = scenario.plan();

      assertThat(plan.problems()).containsExactly(InvalidModuleSetProjectPath.NOT_DIRECTORY);
      assertThat(plan.detailedPlanningStatus()).isEqualTo(ModuleSetDetailedPlanningStatus.NOT_EVALUATED);
      assertThat(plan.executionOrder()).containsExactly(scenario.selected());
      assertThat(plan.dependencyValidations()).isEmpty();
      assertThat(plan.resolvedParameters()).isEmpty();
      assertThat(plan.effectiveParameters().values()).isEmpty();
      assertThat(plan.valid()).isFalse();
    }

    @Test
    void shouldPreserveSelectionProblemsAlongsideInvalidPathWithoutProjectReads() {
      InvalidProjectPathWithSelectionProblemsScenario scenario = invalidProjectPathWithSelectionProblemsScenario();

      ModuleSetPlan plan = scenario.plan();

      assertThat(plan.requestedModules()).isEqualTo(scenario.requestedModules());
      assertThat(plan.executionOrder()).isEmpty();
      assertThat(plan.commitMode()).isEqualTo(ModuleSetCommitMode.ENABLED);
      assertThat(plan.problems()).containsExactly(
        InvalidModuleSetProjectPath.NOT_DIRECTORY,
        new DuplicateRequestedModuleSetModules(List.of(scenario.selected())),
        new UnknownRequestedModuleSetModules(List.of(scenario.unknown()))
      );
      assertThat(plan.dependencyValidations()).isEmpty();
      assertThat(plan.resolvedParameters()).isEmpty();
      assertThat(plan.effectiveParameters().values()).isEmpty();
      assertThat(plan.valid()).isFalse();
    }
  }

  @Nested
  class RequestSelection {

    @Test
    void shouldMarkRequestedHistoricalModuleAsReapplied() {
      ModuleSetSlug selected = slug("selected");

      ModuleSetPlan plan = planning(List.of(module(selected).definition()), List.of(selected))
        .withAppliedModules(selected)
        .plan(selected);

      assertThat(plan.items())
        .singleElement()
        .satisfies(item -> {
          assertThat(item.slug()).isEqualTo(selected);
          assertThat(item.applicationKind()).isEqualTo(ModuleSetApplicationKind.REAPPLICATION);
        });
      assertThat(plan.executionOrder()).containsExactly(selected);
      assertThat(plan.valid()).isTrue();
    }

    @Test
    void shouldRejectExecutionOrderMissingRequestedModule() {
      ModuleSetSlug first = slug("first");
      ModuleSetSlug second = slug("second");
      List<ModuleSetModule> modules = List.of(module(first).definition(), module(second).definition());

      ModuleSetPlan plan = planning(modules, List.of(first)).plan(first, second);

      assertThat(plan.executionOrder()).containsExactly(first);
      assertThat(plan.problems()).containsExactly(new ModuleSetExecutionOrderMismatch(List.of(first, second), List.of(first)));
      assertThat(plan.valid()).isFalse();
    }

    @Test
    void shouldRejectExecutionOrderContainingUnrequestedModule() {
      ModuleSetSlug first = slug("first");
      ModuleSetSlug second = slug("second");
      ModuleSetSlug extra = slug("extra");
      List<ModuleSetModule> modules = List.of(module(first).definition(), module(second).definition(), module(extra).definition());

      ModuleSetPlan plan = planning(catalogIncludingUnrequestedModules(modules, List.of(first, second, extra)))
        .withHistoryReader(projectPath -> {
          throw new AssertionError("History must not be read for an unsafe execution order");
        })
        .plan(first, second);

      assertThat(plan.executionOrder()).containsExactly(first, second, extra);
      assertThat(plan.problems()).containsExactly(
        new ModuleSetExecutionOrderMismatch(List.of(first, second), List.of(first, second, extra))
      );
      assertThat(plan.valid()).isFalse();
    }

    @Test
    void shouldRejectExecutionOrderContainingDuplicateModule() {
      ModuleSetSlug first = slug("first");
      ModuleSetSlug second = slug("second");
      List<ModuleSetModule> modules = List.of(module(first).definition(), module(second).definition());

      ModuleSetPlan plan = planning(modules, List.of(first, second, second))
        .withHistoryReader(projectPath -> {
          throw new AssertionError("History must not be read for an unsafe execution order");
        })
        .plan(first, second);

      assertThat(plan.executionOrder()).containsExactly(first, second, second);
      assertThat(plan.problems()).containsExactly(
        new ModuleSetExecutionOrderMismatch(List.of(first, second), List.of(first, second, second))
      );
      assertThat(plan.valid()).isFalse();
    }
  }

  @Nested
  class ModuleDependencyPlanning {

    @Test
    void shouldSatisfyModuleDependencyWithEarlierRequestedModule() {
      ModuleSetSlug init = slug("init");
      ModuleSetSlug mavenJava = slug("maven-java");
      ModuleSetDependency initDependency = new ModuleSetDependency(ModuleSetDependencyType.MODULE, init.value());
      List<ModuleSetModule> modules = List.of(
        module(init).definition(),
        module(mavenJava).dependingOn(initDependency).providing("java-build-tool").definition()
      );

      ModuleSetPlan plan = planning(modules, List.of(init, mavenJava)).plan(mavenJava, init);

      assertThat(plan.executionOrder()).containsExactly(init, mavenJava);
      assertThat(plan.dependencyValidations())
        .singleElement()
        .satisfies(validation -> {
          assertThat(validation.dependency()).isEqualTo(initDependency);
          assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.SATISFIED_BY_REQUESTED_MODULE);
          assertThat(validation.requiredBy()).containsExactly(mavenJava);
        });
      assertThat(plan.valid()).isTrue();
    }

    @Test
    void shouldPlanRecursiveDependenciesOnceInTokenOrderAndAttributeThemToRequestedModule() {
      RecursiveModuleDependenciesScenario scenario = recursiveModuleDependenciesScenario();

      ModuleSetPlan plan = scenario.plan();

      assertThat(plan.dependencyValidations()).hasSize(2);
      assertThat(plan.dependencyValidations())
        .extracting(validation -> validation.dependency().token())
        .containsExactly("module:direct", "module:transitive");
      assertThat(plan.dependencyValidations()).allSatisfy(validation -> {
        assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.MISSING);
        assertThat(validation.requiredBy()).containsExactly(scenario.requested());
      });
      assertThat(plan.valid()).isFalse();
    }

    @Test
    void shouldReportModuleDependencyMissingFromCatalog() {
      ModuleSetSlug consumer = slug("consumer");
      ModuleSetDependency missing = new ModuleSetDependency(ModuleSetDependencyType.MODULE, "missing-module");
      ModuleSetModule consumerModule = module(consumer).dependingOn(missing).definition();

      ModuleSetPlan plan = planning(List.of(consumerModule), List.of(consumer)).plan(consumer);

      assertThat(plan.dependencyValidations())
        .singleElement()
        .satisfies(validation -> {
          assertThat(validation.dependency()).isEqualTo(missing);
          assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.MISSING);
          assertThat(validation.requiredBy()).containsExactly(consumer);
        });
      assertThat(plan.problems()).isEmpty();
      assertThat(plan.valid()).isFalse();
    }
  }

  @Nested
  class FeatureDependencyPlanning {

    @Test
    void shouldRequireExplicitVisibleFeatureProviderAndListCandidatesAlphabetically() {
      ModuleSetSlug consumer = slug("consumer");
      ModuleSetSlug maven = slug("maven-provider");
      ModuleSetSlug gradle = slug("gradle-provider");
      ModuleSetDependency buildTool = new ModuleSetDependency(ModuleSetDependencyType.FEATURE, "build-tool");
      List<ModuleSetModule> modules = List.of(
        module(consumer).dependingOn(buildTool).definition(),
        module(maven).providing("build-tool").definition(),
        module(gradle).providing("build-tool").definition()
      );

      ModuleSetPlan plan = planning(modules, List.of(gradle, maven, consumer)).plan(consumer);

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
      ModuleSetSlug consumer = slug("consumer");
      ModuleSetSlug provider = slug("provider");
      ModuleSetDependency feature = new ModuleSetDependency(ModuleSetDependencyType.FEATURE, "feature");
      List<ModuleSetModule> modules = List.of(
        module(consumer).dependingOn(feature).definition(),
        module(provider).providing("feature").definition()
      );

      ModuleSetPlan plan = planning(modules, List.of(provider, consumer)).plan(consumer, provider);

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
      ModuleSetSlug consumer = slug("consumer");
      ModuleSetSlug provider = slug("provider");
      ModuleSetDependency feature = new ModuleSetDependency(ModuleSetDependencyType.FEATURE, "feature");
      List<ModuleSetModule> modules = List.of(
        module(consumer).dependingOn(feature).definition(),
        module(provider).providing("feature").definition()
      );

      ModuleSetPlan plan = planning(modules, List.of(consumer, provider)).plan(provider, consumer);

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
    void shouldNotLetModuleSatisfyItsOwnFeatureDependency() {
      ModuleSetSlug selfProvider = slug("self-provider");
      ModuleSetDependency feature = new ModuleSetDependency(ModuleSetDependencyType.FEATURE, "feature");
      ModuleSetModule module = module(selfProvider).dependingOn(feature).providing("feature").definition();

      ModuleSetPlan plan = planning(List.of(module), List.of(selfProvider)).plan(selfProvider);

      assertThat(plan.dependencyValidations())
        .singleElement()
        .satisfies(validation -> {
          assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.MISSING);
          assertThat(validation.candidates()).containsExactly(selfProvider);
          assertThat(validation.provider()).isEmpty();
          assertThat(validation.requiredBy()).containsExactly(selfProvider);
        });
      assertThat(plan.valid()).isFalse();
    }

    @Test
    void shouldSatisfyFeatureDependencyWithProviderFromProjectHistory() {
      ModuleSetSlug consumer = slug("consumer");
      ModuleSetSlug provider = slug("provider");
      ModuleSetDependency feature = new ModuleSetDependency(ModuleSetDependencyType.FEATURE, "feature");
      List<ModuleSetModule> modules = List.of(
        module(consumer).dependingOn(feature).definition(),
        module(provider).providing("feature").definition()
      );

      ModuleSetPlan plan = planning(modules, List.of(provider, consumer)).withAppliedModules(provider).plan(consumer);

      assertThat(plan.dependencyValidations())
        .singleElement()
        .satisfies(validation -> {
          assertThat(validation.status()).isEqualTo(ModuleSetDependencyStatus.SATISFIED_BY_HISTORY);
          assertThat(validation.provider()).contains(provider);
        });
      assertThat(plan.valid()).isTrue();
    }
  }

  @Nested
  class PropertyConflictPlanning {

    @Test
    void shouldKeepPropertyTypeConflictDeterministicWhenModuleOrderIsReversed() {
      ReversedPropertyTypeConflictScenario scenario = reversedPropertyTypeConflictScenario();
      ModuleSetPropertyConflicts expected = new ModuleSetPropertyConflicts(
        List.of(new ModuleSetPropertyTypeConflict(scenario.shared(), List.of(ModuleSetPropertyType.INTEGER, ModuleSetPropertyType.STRING)))
      );

      ModuleSetPlan forwardPlan = scenario.forwardPlan();
      ModuleSetPlan reversedPlan = scenario.reversedPlan();

      assertThat(forwardPlan.problems()).containsExactly(expected);
      assertThat(reversedPlan.problems()).containsExactly(expected);
    }

    @Test
    void shouldKeepDistinctIntegerDefaultLiteralsInPropertyConflict() {
      ModuleSetSlug first = slug("first");
      ModuleSetSlug second = slug("second");
      ModuleSetPropertyKey indentSize = new ModuleSetPropertyKey("indentSize");
      ModuleSetPropertyDefinition firstDefinition = property(indentSize, ModuleSetPropertyType.INTEGER)
        .withDefault(new ModuleSetIntegerParameterValue(2), "2")
        .definition();
      ModuleSetPropertyDefinition secondDefinition = property(indentSize, ModuleSetPropertyType.INTEGER)
        .withDefault(new ModuleSetIntegerParameterValue(2), "02")
        .definition();
      List<ModuleSetModule> modules = List.of(
        module(first).withProperties(firstDefinition).definition(),
        module(second).withProperties(secondDefinition).definition()
      );

      ModuleSetPlan plan = planning(modules, List.of(first, second)).plan(first, second);

      assertThat(plan.problems()).containsExactly(
        new ModuleSetPropertyConflicts(
          List.of(
            new ModuleSetPropertyDefaultConflict(
              indentSize,
              List.of(
                new ModuleSetPropertyDefaultValue(new ModuleSetIntegerParameterValue(2), "02"),
                new ModuleSetPropertyDefaultValue(new ModuleSetIntegerParameterValue(2), "2")
              )
            )
          )
        )
      );
      assertThat(plan.valid()).isFalse();
    }

    @Test
    void shouldReportAllSharedPropertyConflictsAndMissingRequiredValue() {
      ModuleSetSlug first = slug("first");
      ModuleSetSlug second = slug("second");
      ModuleSetPropertyKey shared = new ModuleSetPropertyKey("shared");
      ModuleSetPropertyDefinition firstDefinition = property(shared, ModuleSetPropertyType.STRING)
        .required()
        .describedAs("First description")
        .withDefault(new ModuleSetStringParameterValue("first-default"), "first-default")
        .definition();
      ModuleSetPropertyDefinition secondDefinition = property(shared, ModuleSetPropertyType.STRING)
        .describedAs("Second description")
        .withDefault(new ModuleSetStringParameterValue("second-default"), "second-default")
        .definition();
      List<ModuleSetModule> modules = List.of(
        module(first).withProperties(firstDefinition).definition(),
        module(second).withProperties(secondDefinition).definition()
      );

      ModuleSetPlan plan = planning(modules, List.of(first, second)).plan(first, second);

      assertThat(plan.problems()).containsExactly(
        new ModuleSetPropertyConflicts(
          List.of(
            new ModuleSetPropertyDefaultConflict(
              shared,
              List.of(
                new ModuleSetPropertyDefaultValue(new ModuleSetStringParameterValue("first-default"), "first-default"),
                new ModuleSetPropertyDefaultValue(new ModuleSetStringParameterValue("second-default"), "second-default")
              )
            ),
            new ModuleSetPropertyDescriptionConflict(
              shared,
              List.of(new ModuleSetPropertyDescription("First description"), new ModuleSetPropertyDescription("Second description"))
            )
          )
        )
      );
      assertThat(plan.missingRequiredParameters())
        .extracting(parameter -> parameter.key().value())
        .containsExactly("shared");
      assertThat(plan.valid()).isFalse();
    }
  }

  @Nested
  class ParameterTypePlanning {

    @Test
    void shouldRejectConflictingPropertyTypesWithoutResolvingThatKeyOrReportingItUnused() {
      ConflictingPropertyTypesScenario scenario = conflictingPropertyTypesScenario();

      ModuleSetPlan plan = scenario.plan();

      assertThat(plan.problems()).containsExactly(
        new ModuleSetPropertyConflicts(
          List.of(
            new ModuleSetPropertyTypeConflict(scenario.shared(), List.of(ModuleSetPropertyType.INTEGER, ModuleSetPropertyType.STRING))
          )
        ),
        new UnusedExplicitModuleSetParameters(List.of(scenario.unused()))
      );
      assertThat(plan.resolvedParameters())
        .singleElement()
        .satisfies(parameter -> {
          assertThat(parameter.key()).isEqualTo(scenario.independent());
          assertThat(parameter.value()).isEqualTo(new ModuleSetStringParameterValue("resolved"));
        });
      assertThat(plan.effectiveParameters().values()).containsOnlyKeys(scenario.independent());
      assertThat(plan.missingRequiredParameters()).isEmpty();
      assertThat(plan.valid()).isFalse();
    }

    @Test
    void shouldKeepUnsupportedHistoryParameterScopedToItsOwnProperty() {
      ModuleSetSlug selected = slug("selected");
      ModuleSetPropertyKey unsupported = new ModuleSetPropertyKey("unsupported");
      ModuleSetPropertyKey independent = new ModuleSetPropertyKey("independent");
      ModuleSetPropertyDefinition unsupportedProperty = property(unsupported, ModuleSetPropertyType.STRING).definition();
      ModuleSetPropertyDefinition independentProperty = property(independent, ModuleSetPropertyType.STRING)
        .withDefault(new ModuleSetStringParameterValue("fallback"), "fallback")
        .definition();
      ModuleSetModule selectedModule = module(selected).withProperties(unsupportedProperty, independentProperty).definition();
      ModuleSetHistoryParameters history = new ModuleSetHistoryParameters(
        Map.of(),
        List.of(new UnsupportedModuleSetHistoryParameter(unsupported))
      );

      ModuleSetPlan plan = planning(List.of(selectedModule), List.of(selected)).withHistoryParameters(history).plan(selected);

      assertThat(plan.problems()).containsExactly(
        new ModuleSetHistoryParameterTypeMismatch(unsupported, ModuleSetPropertyType.STRING, ModuleSetHistoryParameterValueType.UNSUPPORTED)
      );
      assertThat(plan.resolvedParameters())
        .singleElement()
        .satisfies(parameter -> {
          assertThat(parameter.key()).isEqualTo(independent);
          assertThat(parameter.value()).isEqualTo(new ModuleSetStringParameterValue("fallback"));
          assertThat(parameter.source()).isEqualTo(ModuleSetPropertySource.DEFAULT);
        });
    }

    @Test
    void shouldRejectExplicitValueWhoseTypeDiffersFromTheReconciledDefinition() {
      ModuleSetSlug selected = slug("selected");
      ModuleSetPropertyKey count = new ModuleSetPropertyKey("count");
      ModuleSetPropertyDefinition property = property(count, ModuleSetPropertyType.INTEGER)
        .withDefault(new ModuleSetIntegerParameterValue(2), "2")
        .definition();
      ModuleSetModule selectedModule = module(selected).withProperties(property).definition();
      ExplicitModuleSetParameters parameters = new ExplicitModuleSetParameters(Map.of(count, new ModuleSetStringParameterValue("4")));

      assertThatThrownBy(() ->
        planning(List.of(selectedModule), List.of(selected))
          .withHistoryParameters(new ModuleSetHistoryParameters(Map.of(count, new ModuleSetIntegerParameterValue(3)), List.of()))
          .planWithParameters(parameters, selected)
      ).isInstanceOfSatisfying(IncompatibleExplicitModuleSetParameterTypeException.class, exception -> {
        assertThat(exception.key()).isEqualTo(count);
        assertThat(exception.expectedType()).isEqualTo(ModuleSetPropertyType.INTEGER);
        assertThat(exception.actualType()).isEqualTo(ModuleSetPropertyType.STRING);
      });
    }

    @Test
    void shouldResolveOptionalIntegerDefaultAsIntegerValue() {
      ModuleSetSlug selected = slug("selected");
      ModuleSetPropertyKey indentSize = new ModuleSetPropertyKey("indentSize");
      ModuleSetPropertyDefinition property = property(indentSize, ModuleSetPropertyType.INTEGER)
        .withDefault(new ModuleSetIntegerParameterValue(2), "2")
        .definition();
      ModuleSetModule selectedModule = module(selected).withProperties(property).definition();

      ModuleSetPlan plan = planning(List.of(selectedModule), List.of(selected)).plan(selected);

      assertThat(plan.resolvedParameters())
        .singleElement()
        .satisfies(parameter -> assertThat(parameter.value()).isEqualTo(new ModuleSetIntegerParameterValue(2)));
      assertThat(plan.valid()).isTrue();
    }
  }

  @Nested
  class ParameterResolutionPlanning {

    @Test
    void shouldResolveSharedPropertyOnceWithExplicitInputBeforeHistory() {
      ExplicitInputBeforeHistoryScenario scenario = explicitInputBeforeHistoryScenario();

      ModuleSetPlan plan = scenario.plan();

      assertThat(plan.resolvedParameters())
        .singleElement()
        .satisfies(parameter -> {
          assertThat(parameter.key()).isEqualTo(scenario.packageName());
          assertThat(parameter.value()).isEqualTo(new ModuleSetStringParameterValue("com.explicit"));
          assertThat(parameter.source()).isEqualTo(ModuleSetPropertySource.EXPLICIT_INPUT);
        });
      assertThat(plan.missingRequiredParameters()).isEmpty();
      assertThat(plan.valid()).isTrue();
    }

    @Test
    void shouldRejectKnownPropertyOptionUnusedByRequestedModules() {
      ModuleSetSlug selected = slug("selected");
      ModuleSetSlug unselected = slug("unselected");
      ModuleSetPropertyKey unused = new ModuleSetPropertyKey("unusedProperty");
      ModuleSetPropertyDefinition unusedDefinition = property(unused, ModuleSetPropertyType.STRING).definition();
      List<ModuleSetModule> modules = List.of(
        module(selected).definition(),
        module(unselected).withProperties(unusedDefinition).definition()
      );
      ExplicitModuleSetParameters parameters = new ExplicitModuleSetParameters(Map.of(unused, new ModuleSetStringParameterValue("value")));

      ModuleSetPlan plan = planning(modules, List.of(selected, unselected)).planWithParameters(parameters, selected);

      assertThat(plan.problems()).containsExactly(new UnusedExplicitModuleSetParameters(List.of(unused)));
      assertThat(plan.valid()).isFalse();
    }

    @Test
    void shouldUseHistoryBeforeOptionalDefaultAndKeepMandatoryDefaultInformational() {
      HistoryBeforeOptionalDefaultScenario scenario = historyBeforeOptionalDefaultScenario();

      ModuleSetPlan plan = scenario.plan();

      assertThat(plan.resolvedParameters())
        .extracting(ResolvedModuleSetParameter::value)
        .containsExactly(new ModuleSetStringParameterValue("from-history"), new ModuleSetStringParameterValue("optional-default"));
      assertThat(plan.resolvedParameters())
        .extracting(ResolvedModuleSetParameter::source)
        .containsExactly(ModuleSetPropertySource.PROJECT_HISTORY, ModuleSetPropertySource.DEFAULT);
      assertThat(plan.effectiveParameters().values())
        .containsExactlyEntriesOf(Map.of(scenario.historyKey(), new ModuleSetStringParameterValue("from-history")))
        .doesNotContainKeys(scenario.defaultKey(), scenario.mandatoryKey());
      assertThat(plan.missingRequiredParameters())
        .extracting(parameter -> parameter.key().value())
        .containsExactly("mandatoryValue");
      assertThat(plan.valid()).isFalse();
    }
  }
}
