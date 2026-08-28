package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionEventListener;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionResult;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleApplication;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleApplier;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleExecutionCompleted;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleExecutionStarted;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleResult;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanItem;
import com.seed4j.cli.shared.error.domain.Assert;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModuleSetExecutionApplicationService {

  private final ModuleSetModuleApplier moduleApplier;

  public ModuleSetExecutionApplicationService(ModuleSetModuleApplier moduleApplier) {
    Assert.notNull("moduleApplier", moduleApplier);
    this.moduleApplier = moduleApplier;
  }

  public ModuleSetExecutionResult execute(ModuleSetPlan plan, ModuleSetExecutionEventListener eventListener) {
    validateExecution(plan, eventListener);

    ExecutionProgress progress = ExecutionProgress.empty(eventListener);
    for (int index = 0; index < plan.items().size(); index++) {
      ModuleSetPlanItem item = plan.items().get(index);
      ModuleSetModuleStatus status = apply(plan, item, eventListener);
      progress = progress.complete(item, status);
      if (status == ModuleSetModuleStatus.FAILED) {
        return partialFailure(plan, index, progress);
      }
    }
    return progress.result(ModuleSetExecutionStatus.SUCCEEDED);
  }

  private static void validateExecution(ModuleSetPlan plan, ModuleSetExecutionEventListener eventListener) {
    Assert.notNull("plan", plan);
    Assert.notNull("eventListener", eventListener);
    if (!plan.valid()) {
      throw new IllegalArgumentException("Only a valid module set plan can be executed");
    }
  }

  private static ModuleSetExecutionResult partialFailure(ModuleSetPlan plan, int failedIndex, ExecutionProgress progress) {
    return progress
      .complete(plan.items().subList(failedIndex + 1, plan.items().size()), ModuleSetModuleStatus.SKIPPED)
      .result(ModuleSetExecutionStatus.PARTIAL_FAILURE);
  }

  private ModuleSetModuleStatus apply(ModuleSetPlan plan, ModuleSetPlanItem item, ModuleSetExecutionEventListener eventListener) {
    eventListener.on(new ModuleSetModuleExecutionStarted(item));
    try {
      moduleApplier.apply(new ModuleSetModuleApplication(item.slug(), plan.projectPath(), plan.commitMode(), plan.effectiveParameters()));
      return ModuleSetModuleStatus.SUCCEEDED;
    } catch (RuntimeException _) {
      return ModuleSetModuleStatus.FAILED;
    }
  }

  private record ExecutionProgress(List<ModuleSetModuleResult> results, ModuleSetExecutionEventListener eventListener) {
    private ExecutionProgress {
      results = List.copyOf(results);
    }

    private static ExecutionProgress empty(ModuleSetExecutionEventListener eventListener) {
      return new ExecutionProgress(List.of(), eventListener);
    }

    private ExecutionProgress complete(ModuleSetPlanItem item, ModuleSetModuleStatus status) {
      ModuleSetModuleResult result = new ModuleSetModuleResult(item, status);
      List<ModuleSetModuleResult> nextResults = new ArrayList<>(results);
      nextResults.add(result);
      eventListener.on(new ModuleSetModuleExecutionCompleted(item, status));
      return new ExecutionProgress(nextResults, eventListener);
    }

    private ExecutionProgress complete(List<ModuleSetPlanItem> items, ModuleSetModuleStatus status) {
      ExecutionProgress progress = this;
      for (ModuleSetPlanItem item : items) {
        progress = progress.complete(item, status);
      }
      return progress;
    }

    private ModuleSetExecutionResult result(ModuleSetExecutionStatus status) {
      return new ModuleSetExecutionResult(results, status);
    }
  }
}
