package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.Seed4JCliRuntime;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public class PreSpringRuntimeEnvironmentSeed4JCliRuntime implements Seed4JCliRuntime {

  private final PreSpringRuntimeEnvironment runtimeEnvironment;

  public PreSpringRuntimeEnvironmentSeed4JCliRuntime(PreSpringRuntimeEnvironment runtimeEnvironment) {
    Assert.notNull("runtimeEnvironment", runtimeEnvironment);
    this.runtimeEnvironment = runtimeEnvironment;
  }

  @Override
  public Path executableJar() {
    return runtimeEnvironment.executablePath();
  }

  @Override
  public boolean childRuntime() {
    return runtimeEnvironment.childMode();
  }
}
