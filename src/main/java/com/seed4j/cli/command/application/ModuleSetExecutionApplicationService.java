package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.moduleset.EffectiveModuleSetParameters;
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
    Assert.notNull("plan", plan);
    Assert.notNull("eventListener", eventListener);
    if (!plan.valid()) {
      throw new IllegalArgumentException("Only a valid module set plan can be executed");
    }

    EffectiveModuleSetParameters effectiveParameters = plan.effectiveParameters();
    List<ModuleSetModuleResult> results = new ArrayList<>();
    for (int index = 0; index < plan.items().size(); index++) {
      ModuleSetPlanItem item = plan.items().get(index);
      eventListener.on(new ModuleSetModuleExecutionStarted(item));
      try {
        moduleApplier.apply(new ModuleSetModuleApplication(item.slug(), plan.projectPath(), plan.commitMode(), effectiveParameters));
      } catch (RuntimeException exception) {
        ModuleSetModuleResult failed = new ModuleSetModuleResult(item, ModuleSetModuleStatus.FAILED);
        results.add(failed);
        eventListener.on(new ModuleSetModuleExecutionCompleted(item, failed.status()));
        for (ModuleSetPlanItem skippedItem : plan.items().subList(index + 1, plan.items().size())) {
          ModuleSetModuleResult skipped = new ModuleSetModuleResult(skippedItem, ModuleSetModuleStatus.SKIPPED);
          results.add(skipped);
          eventListener.on(new ModuleSetModuleExecutionCompleted(skippedItem, skipped.status()));
        }
        return new ModuleSetExecutionResult(results, ModuleSetExecutionStatus.PARTIAL_FAILURE);
      }
      ModuleSetModuleResult result = new ModuleSetModuleResult(item, ModuleSetModuleStatus.SUCCEEDED);
      results.add(result);
      eventListener.on(new ModuleSetModuleExecutionCompleted(item, result.status()));
    }
    return new ModuleSetExecutionResult(results, ModuleSetExecutionStatus.SUCCEEDED);
  }
}
