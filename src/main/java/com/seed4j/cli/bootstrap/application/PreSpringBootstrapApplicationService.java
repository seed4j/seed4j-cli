package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.shared.error.domain.Assert;

public class PreSpringBootstrapApplicationService {

  private final PreSpringLauncherFactory preSpringLauncherFactory;
  private final PreSpringRuntimeEnvironmentProvider preSpringRuntimeEnvironmentProvider;

  public PreSpringBootstrapApplicationService(
    PreSpringLauncherFactory preSpringLauncherFactory,
    PreSpringRuntimeEnvironmentProvider preSpringRuntimeEnvironmentProvider
  ) {
    Assert.notNull("preSpringLauncherFactory", preSpringLauncherFactory);
    Assert.notNull("preSpringRuntimeEnvironmentProvider", preSpringRuntimeEnvironmentProvider);
    this.preSpringLauncherFactory = preSpringLauncherFactory;
    this.preSpringRuntimeEnvironmentProvider = preSpringRuntimeEnvironmentProvider;
  }

  public int exitCodeFor(PreSpringBootstrapCommand command) {
    Assert.notNull("command", command);
    PreSpringRuntimeEnvironment runtimeEnvironment = preSpringRuntimeEnvironmentProvider.current();
    PreSpringLauncher preSpringLauncher = preSpringLauncherFactory.create(
      runtimeEnvironment.userHomePath(),
      runtimeEnvironment.executablePath(),
      runtimeEnvironment.currentSeed4JVersion(),
      runtimeEnvironment.javaExecutablePath()
    );
    return preSpringLauncher.launch(command.args(), runtimeEnvironment.childMode());
  }
}
