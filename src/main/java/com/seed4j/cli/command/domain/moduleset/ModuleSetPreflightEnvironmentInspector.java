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
    return switch (projectPathValidator.validate(projectPath)) {
      case VALID -> List.of();
      case NOT_DIRECTORY -> List.of(InvalidModuleSetProjectPath.NOT_DIRECTORY);
      case NOT_ACCESSIBLE -> List.of(InvalidModuleSetProjectPath.NOT_ACCESSIBLE);
      case NOT_APPARENTLY_CREATABLE -> List.of(InvalidModuleSetProjectPath.NOT_APPARENTLY_CREATABLE);
    };
  }

  List<ModuleSetPlanningWarning> warnings(ModuleSetPlan plan) {
    if (!plan.valid() || plan.commitMode().disabled()) {
      return List.of();
    }
    return gitStateReader.state(plan.projectPath()) == ModuleSetGitState.DIRTY ? List.of(new DirtyModuleSetGitWorktree()) : List.of();
  }
}
