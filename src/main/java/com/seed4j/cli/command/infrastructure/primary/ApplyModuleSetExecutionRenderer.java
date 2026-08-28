package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetCommitMode;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionEvent;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionResult;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleExecutionCompleted;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleExecutionStarted;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleResult;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanItem;
import com.seed4j.cli.shared.error.domain.Assert;
import java.util.LinkedHashMap;
import java.util.Map;

final class ApplyModuleSetExecutionRenderer {

  static final String START = "\nApplying module set:\n";

  private final ModuleSetCommitMode commitMode;
  private final Map<ModuleSetPlanItem, Integer> positions;
  private final int totalItems;

  ApplyModuleSetExecutionRenderer(ModuleSetPlan plan) {
    Assert.notNull("plan", plan);
    commitMode = plan.commitMode();
    positions = executionPositions(plan);
    totalItems = plan.items().size();
  }

  private static Map<ModuleSetPlanItem, Integer> executionPositions(ModuleSetPlan plan) {
    Map<ModuleSetPlanItem, Integer> positions = new LinkedHashMap<>();
    for (int index = 0; index < plan.items().size(); index++) {
      positions.putIfAbsent(plan.items().get(index), index + 1);
    }
    return Map.copyOf(positions);
  }

  String event(ModuleSetExecutionEvent event) {
    return switch (event) {
      case ModuleSetModuleExecutionStarted started -> started(started);
      case ModuleSetModuleExecutionCompleted completed -> completed(completed);
    };
  }

  private String started(ModuleSetModuleExecutionStarted started) {
    return itemLine(started.item());
  }

  private String completed(ModuleSetModuleExecutionCompleted completed) {
    return completed(completed.item(), completed.status());
  }

  private String itemLine(ModuleSetPlanItem item) {
    return (
      "[%d/%d] %s%s".formatted(positions.getOrDefault(item, 0), totalItems, item.slug().value(), item.reapplied() ? " (reapplied)" : "")
      + '\n'
    );
  }

  private String completed(ModuleSetPlanItem item, ModuleSetModuleStatus status) {
    StringBuilder output = new StringBuilder();
    if (status == ModuleSetModuleStatus.SKIPPED) {
      output.append(itemLine(item));
    }
    output.append("      Status: ").append(status).append('\n');
    String details = switch (status) {
      case SUCCEEDED -> "      History: updated\n"
        + "      Events: dispatched\n"
        + "      Commit: "
        + (commitMode.enabled() ? "created" : "disabled")
        + '\n';
      case FAILED -> "      Effects: indeterminate\n";
      case SKIPPED -> "      Reason: not invoked after the first failure\n";
    };
    return output.append(details).toString();
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
    return "ERROR: %s failed: unable to complete module application.".formatted(failed.item().slug().value()) + '\n' + failureGuidance();
  }

  private String failureGuidance() {
    return switch (commitMode) {
      case ENABLED -> "The failed module may have changed files, history, Git, dispatched events, or downstream event effects. Earlier successes were preserved.\n"
        + "Next action: inspect the working tree, project history, Git log, and relevant event effects before deciding whether to retry.\n";
      case DISABLED -> "The failed module may have changed files, history, dispatched events, or downstream event effects. Earlier successes were preserved.\n"
        + "Next action: inspect the working tree, project history, and relevant event effects before deciding whether to retry.\n";
    };
  }

  boolean failed(ModuleSetExecutionResult result) {
    return result.status() == ModuleSetExecutionStatus.PARTIAL_FAILURE;
  }
}
