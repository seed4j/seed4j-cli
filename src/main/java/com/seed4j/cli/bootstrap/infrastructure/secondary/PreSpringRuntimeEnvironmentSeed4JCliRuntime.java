package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.Seed4JCliExecutablePath;
import com.seed4j.cli.bootstrap.domain.Seed4JCliRuntime;
import com.seed4j.cli.shared.error.domain.Assert;

public class PreSpringRuntimeEnvironmentSeed4JCliRuntime implements Seed4JCliRuntime {

  private final PreSpringRuntimeEnvironment runtimeEnvironment;

  public PreSpringRuntimeEnvironmentSeed4JCliRuntime(PreSpringRuntimeEnvironment runtimeEnvironment) {
    Assert.notNull("runtimeEnvironment", runtimeEnvironment);
    this.runtimeEnvironment = runtimeEnvironment;
  }

  @Override
  public Seed4JCliExecutablePath executableJar() {
    return runtimeEnvironment.executablePath();
  }

  @Override
  public boolean childRuntime() {
    return runtimeEnvironment.processMode().child();
  }
}
