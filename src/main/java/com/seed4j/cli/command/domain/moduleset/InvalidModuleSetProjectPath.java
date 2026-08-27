package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record InvalidModuleSetProjectPath(ModuleSetProjectPathStatus status) implements ModuleSetPlanningProblem {
  public InvalidModuleSetProjectPath {
    Assert.notNull("status", status);
  }
}
