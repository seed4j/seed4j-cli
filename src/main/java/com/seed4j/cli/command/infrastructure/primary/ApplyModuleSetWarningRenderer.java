package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.moduleset.DirtyModuleSetGitWorktree;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningWarning;

final class ApplyModuleSetWarningRenderer {

  String planning(ModuleSetPlan plan) {
    return render(
      plan,
      "module commits in a later execution can include or be affected by pre-existing changes. This plan is read-only; no modules will be applied."
    );
  }

  String execution(ModuleSetPlan plan) {
    return render(plan, "module commits can include or be affected by pre-existing changes. Execution will continue automatically.");
  }

  private static String render(ModuleSetPlan plan, String message) {
    StringBuilder output = new StringBuilder();
    for (ModuleSetPlanningWarning warning : plan.warnings()) {
      switch (warning) {
        case DirtyModuleSetGitWorktree _ -> output
          .append("WARNING: Git worktree ")
          .append(plan.projectPath().value())
          .append(" is dirty; ")
          .append(message)
          .append('\n');
      }
    }
    return output.toString();
  }
}
