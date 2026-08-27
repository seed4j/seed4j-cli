package com.seed4j.cli.command.domain.moduleset;

import java.util.List;

final class ModuleSetPreflightEnvironmentInspector {

  private final ModuleSetProjectPathValidator projectPathValidator;
  private final ModuleSetGitStateReader gitStateReader;

  ModuleSetPreflightEnvironmentInspector(ModuleSetProjectPathValidator projectPathValidator, ModuleSetGitStateReader gitStateReader) {
    this.projectPathValidator = projectPathValidator;
    this.gitStateReader = gitStateReader;
  }

  List<ModuleSetPlanningProblem> pathProblems(ModuleSetProjectPath projectPath) {
    ModuleSetProjectPathStatus status = projectPathValidator.validate(projectPath);
    return status.valid() ? List.of() : List.of(new InvalidModuleSetProjectPath(status));
  }

  List<ModuleSetPlanningWarning> warnings(ModuleSetPlan plan) {
    if (!plan.valid() || plan.commitMode().disabled()) {
      return List.of();
    }
    return gitStateReader.state(plan.projectPath()) == ModuleSetGitState.DIRTY ? List.of(new DirtyModuleSetGitWorktree()) : List.of();
  }
}
