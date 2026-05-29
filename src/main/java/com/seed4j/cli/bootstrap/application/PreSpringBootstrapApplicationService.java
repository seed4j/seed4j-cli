package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironmentReader;
import com.seed4j.cli.shared.error.domain.Assert;

public class PreSpringBootstrapApplicationService {

  private final PreSpringLauncherFactory preSpringLauncherFactory;
  private final PreSpringRuntimeEnvironmentReader preSpringRuntimeEnvironmentReader;

  public PreSpringBootstrapApplicationService(
    PreSpringLauncherFactory preSpringLauncherFactory,
    PreSpringRuntimeEnvironmentReader preSpringRuntimeEnvironmentReader
  ) {
    Assert.notNull("preSpringLauncherFactory", preSpringLauncherFactory);
    Assert.notNull("preSpringRuntimeEnvironmentReader", preSpringRuntimeEnvironmentReader);
    this.preSpringLauncherFactory = preSpringLauncherFactory;
    this.preSpringRuntimeEnvironmentReader = preSpringRuntimeEnvironmentReader;
  }

  public int exitCodeFor(PreSpringBootstrapCommand command) {
    Assert.notNull("command", command);
    PreSpringRuntimeEnvironment runtimeEnvironment = preSpringRuntimeEnvironmentReader.current();
    PreSpringLauncher preSpringLauncher = preSpringLauncherFactory.create(
      runtimeEnvironment.userHomePath(),
      runtimeEnvironment.executablePath(),
      runtimeEnvironment.currentSeed4JVersion(),
      runtimeEnvironment.javaExecutablePath()
    );
    return preSpringLauncher.launch(command.args(), runtimeEnvironment.childMode());
  }
}
