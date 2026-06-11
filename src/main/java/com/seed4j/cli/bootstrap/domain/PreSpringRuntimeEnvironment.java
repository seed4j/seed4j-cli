package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record PreSpringRuntimeEnvironment(
  Seed4JCliHome cliHome,
  Seed4JCliExecutablePath executablePath,
  RuntimeProcessMode processMode,
  JavaExecutablePath javaExecutablePath
) {
  public PreSpringRuntimeEnvironment {
    Assert.notNull("cliHome", cliHome);
    Assert.notNull("executablePath", executablePath);
    Assert.notNull("processMode", processMode);
    Assert.notNull("javaExecutablePath", javaExecutablePath);
  }
}
