package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetModuleApplication(
  ModuleSetSlug slug,
  ModuleSetProjectPath projectPath,
  ModuleSetCommitMode commitMode,
  EffectiveModuleSetParameters effectiveParameters
) {
  public ModuleSetModuleApplication {
    Assert.notNull("slug", slug);
    Assert.notNull("projectPath", projectPath);
    Assert.notNull("commitMode", commitMode);
    Assert.notNull("effectiveParameters", effectiveParameters);
  }
}
