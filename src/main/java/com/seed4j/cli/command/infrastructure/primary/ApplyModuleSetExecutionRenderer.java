package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionEvent;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionResult;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleExecutionCompleted;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleExecutionStarted;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleResult;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanItem;

final class ApplyModuleSetExecutionRenderer {

  String start() {
    return "\nApplying module set:\n";
  }

  String event(ModuleSetPlan plan, ModuleSetExecutionEvent event) {
    return switch (event) {
      case ModuleSetModuleExecutionStarted(ModuleSetPlanItem item) -> itemLine(plan, item);
      case ModuleSetModuleExecutionCompleted(ModuleSetPlanItem item, ModuleSetModuleStatus status) -> completed(plan, item, status);
    };
  }

  private static String itemLine(ModuleSetPlan plan, ModuleSetPlanItem item) {
    return "[%d/%d] %s%s\n".formatted(
      plan.items().indexOf(item) + 1,
      plan.items().size(),
      item.slug().value(),
      item.reapplied() ? " (reapplied)" : ""
    );
  }

  private static String completed(ModuleSetPlan plan, ModuleSetPlanItem item, ModuleSetModuleStatus status) {
    StringBuilder output = new StringBuilder();
    if (status == ModuleSetModuleStatus.SKIPPED) {
      output.append(itemLine(plan, item));
    }
    output.append("      Status: ").append(status).append('\n');
    switch (status) {
      case SUCCEEDED -> output
        .append("      History: updated\n")
        .append("      Events: dispatched\n")
        .append("      Commit: ")
        .append(plan.commitMode().enabled() ? "created" : "disabled")
        .append('\n');
      case FAILED -> output.append("      Effects: indeterminate\n");
      case SKIPPED -> output.append("      Reason: not invoked after the first failure\n");
    }
    return output.toString();
  }

  String summary(ModuleSetExecutionResult result) {
    StringBuilder output = new StringBuilder("\nSummary:\n");
    for (ModuleSetModuleResult module : result.modules()) {
      output
        .append("  ")
        .append(module.item().slug().value())
        .append("  ")
        .append(module.status())
        .append(module.item().reapplied() ? "  reapplied" : "")
        .append('\n');
    }
    return output.append("Module set status: ").append(result.status()).append('\n').toString();
  }

  String failure(ModuleSetExecutionResult result) {
    ModuleSetModuleResult failed = result
      .modules()
      .stream()
      .filter(module -> module.status() == ModuleSetModuleStatus.FAILED)
      .findFirst()
      .orElseThrow();
    return (
      "ERROR: %s failed: unable to complete module application.\n".formatted(failed.item().slug().value())
      + "The failed module may have changed files, history, Git, dispatched events, or downstream event effects. Earlier successes were preserved.\n"
      + "Next action: inspect the working tree, project history, Git log, and relevant event effects before deciding whether to retry.\n"
    );
  }

  boolean failed(ModuleSetExecutionResult result) {
    return result.status() == ModuleSetExecutionStatus.PARTIAL_FAILURE;
  }
}
