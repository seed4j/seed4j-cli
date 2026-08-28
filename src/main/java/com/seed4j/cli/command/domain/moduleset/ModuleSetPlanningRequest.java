package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetPlanningRequest(
  RequestedModuleSet requestedModules,
  ModuleSetProjectPath projectPath,
  ExplicitModuleSetParameters explicitParameters,
  ModuleSetCommitMode commitMode
) {
  public ModuleSetPlanningRequest {
    Assert.notNull("requestedModules", requestedModules);
    Assert.notNull("projectPath", projectPath);
    Assert.notNull("explicitParameters", explicitParameters);
    Assert.notNull("commitMode", commitMode);
  }
}
