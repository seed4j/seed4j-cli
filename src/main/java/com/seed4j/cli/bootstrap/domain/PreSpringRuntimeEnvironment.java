package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record PreSpringRuntimeEnvironment(Path userHomePath, Path executablePath, boolean childMode, Path javaExecutablePath) {
  public PreSpringRuntimeEnvironment {
    Assert.notNull("userHomePath", userHomePath);
    Assert.notNull("executablePath", executablePath);
    Assert.notNull("javaExecutablePath", javaExecutablePath);
  }
}
