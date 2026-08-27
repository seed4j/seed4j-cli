package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.moduleset.DirtyModuleSetGitWorktree;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningWarning;

final class ApplyModuleSetWarningRenderer {

  String render(ModuleSetPlan plan) {
    StringBuilder output = new StringBuilder();
    for (ModuleSetPlanningWarning warning : plan.warnings()) {
      switch (warning) {
        case DirtyModuleSetGitWorktree _ -> output
          .append("WARNING: Git worktree ")
          .append(plan.projectPath().value())
          .append(" is dirty; module commits can include or be affected by pre-existing changes. ")
          .append("Execution will continue automatically.\n");
      }
    }
    return output.toString();
  }
}
