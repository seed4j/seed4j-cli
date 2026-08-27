package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetBooleanParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetIntegerParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanItem;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertySource;
import com.seed4j.cli.command.domain.moduleset.ModuleSetStringParameterValue;
import com.seed4j.cli.command.domain.moduleset.ResolvedModuleSetParameter;
import java.util.List;

final class ApplyModuleSetPreflightSectionsRenderer {

  private ApplyModuleSetPreflightSectionsRenderer() {}

  static void appendExecutionOrder(StringBuilder output, List<ModuleSetPlanItem> items) {
    output.append("Execution order:\n");
    for (int index = 0; index < items.size(); index++) {
      ModuleSetPlanItem item = items.get(index);
      output
        .append("  ")
        .append(index + 1)
        .append(". ")
        .append(item.slug().value())
        .append(item.reapplied() ? " (reapplied)" : "")
        .append('\n');
    }
    output.append('\n');
  }

  static void appendParameters(StringBuilder output, String heading, List<ResolvedModuleSetParameter> parameters) {
    output.append(heading).append(":\n");
    if (parameters.isEmpty()) {
      output.append("  (none)\n");
    }
    for (ResolvedModuleSetParameter parameter : parameters) {
      output
        .append("  ✓ ")
        .append(parameter.key().value())
        .append(": ")
        .append(parameterValue(parameter.value()))
        .append("\n    Source: ")
        .append(source(parameter.source()))
        .append("\n    CLI option: ")
        .append(ModulePropertyOptionSpecFactory.toDashedFormat(parameter.key().value()))
        .append('\n');
    }
    output.append('\n');
  }

  private static String parameterValue(ModuleSetParameterValue value) {
    return switch (value) {
      case ModuleSetStringParameterValue(String stringValue) -> stringValue;
      case ModuleSetIntegerParameterValue(Integer integerValue) -> integerValue.toString();
      case ModuleSetBooleanParameterValue(Boolean booleanValue) -> booleanValue.toString();
    };
  }

  private static String source(ModuleSetPropertySource source) {
    return switch (source) {
      case EXPLICIT_INPUT -> "explicit CLI input";
      case PROJECT_HISTORY -> "project history";
      case DEFAULT -> "default (informational)";
    };
  }

  static void appendCommitMode(StringBuilder output, ModuleSetPlan plan) {
    output
      .append("Commit mode: ")
      .append(
        plan.commitMode().enabled()
          ? "one commit per succeeded module"
          : "disabled; Git will not be initialized and no commits will be created"
      )
      .append('\n');
  }
}
