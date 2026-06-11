package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record PreSpringRuntimeEnvironment(Seed4JCliHome cliHome, Path executablePath, boolean childMode, Path javaExecutablePath) {
  public PreSpringRuntimeEnvironment {
    Assert.notNull("cliHome", cliHome);
    Assert.notNull("executablePath", executablePath);
    Assert.notNull("javaExecutablePath", javaExecutablePath);
  }
}
