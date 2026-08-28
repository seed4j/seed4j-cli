package com.seed4j.cli.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.domain.moduleset.ExplicitModuleSetParameters;
import com.seed4j.cli.command.domain.moduleset.ModuleSetCatalog;
import com.seed4j.cli.command.domain.moduleset.ModuleSetCommitMode;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionEvent;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionResult;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetGitState;
import com.seed4j.cli.command.domain.moduleset.ModuleSetHistoryParameters;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModule;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleApplication;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleExecutionCompleted;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleExecutionStarted;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistory;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningRequest;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPathStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.RequestedModuleSet;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

@UnitTest
class ModuleSetExecutionApplicationServiceTest {

  @Test
  void shouldApplyEveryPlannedModuleSequentiallyAndReportSuccess() {
    ModuleSetSlug first = new ModuleSetSlug("first");
    ModuleSetSlug second = new ModuleSetSlug("second");
    ModuleSetPlan plan = plan(List.of(first, second));
    List<ModuleSetModuleApplication> applications = new ArrayList<>();
    List<ModuleSetExecutionEvent> events = new ArrayList<>();
    ModuleSetExecutionApplicationService execution = new ModuleSetExecutionApplicationService(applications::add);

    ModuleSetExecutionResult result = execution.execute(plan, events::add);

    assertThat(applications).extracting(ModuleSetModuleApplication::slug).containsExactly(first, second);
    assertThat(applications).allSatisfy(application -> {
      assertThat(application.projectPath()).isEqualTo(plan.projectPath());
      assertThat(application.commitMode()).isEqualTo(ModuleSetCommitMode.ENABLED);
      assertThat(application.effectiveParameters()).isSameAs(plan.effectiveParameters());
    });
    assertThat(result.status()).isEqualTo(ModuleSetExecutionStatus.SUCCEEDED);
    assertThat(result.modules())
      .extracting(module -> module.status())
      .containsExactly(ModuleSetModuleStatus.SUCCEEDED, ModuleSetModuleStatus.SUCCEEDED);
    assertThat(events).containsExactly(
      new ModuleSetModuleExecutionStarted(plan.items().get(0)),
      new ModuleSetModuleExecutionCompleted(plan.items().get(0), ModuleSetModuleStatus.SUCCEEDED),
      new ModuleSetModuleExecutionStarted(plan.items().get(1)),
      new ModuleSetModuleExecutionCompleted(plan.items().get(1), ModuleSetModuleStatus.SUCCEEDED)
    );
  }

  @Test
  void shouldStopAtFirstFailureAndReportEveryPlannedModule() {
    ModuleSetSlug first = new ModuleSetSlug("first");
    ModuleSetSlug second = new ModuleSetSlug("second");
    ModuleSetSlug third = new ModuleSetSlug("third");
    ModuleSetPlan plan = plan(List.of(first, second, third));
    List<ModuleSetSlug> appliedModules = new ArrayList<>();
    List<ModuleSetExecutionEvent> events = new ArrayList<>();
    ModuleSetExecutionApplicationService execution = new ModuleSetExecutionApplicationService(application -> {
      appliedModules.add(application.slug());
      if (application.slug().equals(second)) {
        throw new IllegalStateException("second failed");
      }
    });

    ModuleSetExecutionResult result = execution.execute(plan, events::add);

    assertThat(appliedModules).containsExactly(first, second);
    assertThat(result.status()).isEqualTo(ModuleSetExecutionStatus.PARTIAL_FAILURE);
    assertThat(result.modules())
      .extracting(module -> module.status())
      .containsExactly(ModuleSetModuleStatus.SUCCEEDED, ModuleSetModuleStatus.FAILED, ModuleSetModuleStatus.SKIPPED);
    assertThat(events).containsExactly(
      new ModuleSetModuleExecutionStarted(plan.items().get(0)),
      new ModuleSetModuleExecutionCompleted(plan.items().get(0), ModuleSetModuleStatus.SUCCEEDED),
      new ModuleSetModuleExecutionStarted(plan.items().get(1)),
      new ModuleSetModuleExecutionCompleted(plan.items().get(1), ModuleSetModuleStatus.FAILED),
      new ModuleSetModuleExecutionCompleted(plan.items().get(2), ModuleSetModuleStatus.SKIPPED)
    );
  }

  @Test
  void shouldRejectInvalidPlanBeforeApplyingModulesOrPublishingEvents() {
    ModuleSetSlug selected = new ModuleSetSlug("selected");
    ModuleSetCatalog catalog = new ModuleSetCatalog() {
      @Override
      public List<ModuleSetModule> modules() {
        return List.of(new ModuleSetModule(selected, List.of(), List.of(), Optional.empty()));
      }

      @Override
      public List<ModuleSetSlug> sort(List<ModuleSetSlug> requestedModules) {
        return List.of(selected);
      }
    };
    ModuleSetPlanningApplicationService planning = new ModuleSetPlanningApplicationService(
      catalog,
      projectPath -> new ModuleSetPlanningHistory(Set.of(), new ModuleSetHistoryParameters(Map.of(), List.of())),
      projectPath -> ModuleSetProjectPathStatus.NOT_DIRECTORY,
      projectPath -> ModuleSetGitState.NO_WORKTREE
    );
    ModuleSetPlan invalidPlan = planning.plan(
      new ModuleSetPlanningRequest(
        new RequestedModuleSet(List.of(selected)),
        new ModuleSetProjectPath(Path.of("project.txt")),
        ExplicitModuleSetParameters.empty(),
        ModuleSetCommitMode.ENABLED
      )
    );
    List<ModuleSetModuleApplication> applications = new ArrayList<>();
    List<ModuleSetExecutionEvent> events = new ArrayList<>();
    ModuleSetExecutionApplicationService execution = new ModuleSetExecutionApplicationService(applications::add);

    assertThatThrownBy(() -> execution.execute(invalidPlan, events::add))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Only a valid module set plan can be executed");

    assertThat(applications).isEmpty();
    assertThat(events).isEmpty();
  }

  private static ModuleSetPlan plan(List<ModuleSetSlug> executionOrder) {
    List<ModuleSetModule> modules = executionOrder
      .stream()
      .map(slug -> new ModuleSetModule(slug, List.of(), List.of(), Optional.empty()))
      .toList();
    ModuleSetCatalog catalog = new ModuleSetCatalog() {
      @Override
      public List<ModuleSetModule> modules() {
        return modules;
      }

      @Override
      public List<ModuleSetSlug> sort(List<ModuleSetSlug> requestedModules) {
        return executionOrder;
      }
    };
    ModuleSetPlanningApplicationService planning = new ModuleSetPlanningApplicationService(
      catalog,
      projectPath -> new ModuleSetPlanningHistory(Set.of(), new ModuleSetHistoryParameters(Map.of(), List.of())),
      projectPath -> ModuleSetProjectPathStatus.VALID,
      projectPath -> ModuleSetGitState.NO_WORKTREE
    );
    return planning.plan(
      new ModuleSetPlanningRequest(
        new RequestedModuleSet(executionOrder),
        new ModuleSetProjectPath(Path.of(".")),
        ExplicitModuleSetParameters.empty(),
        ModuleSetCommitMode.ENABLED
      )
    );
  }
}
