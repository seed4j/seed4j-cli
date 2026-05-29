package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.shared.error.domain.Assert;

public class PreSpringBootstrapApplicationService {

  private final PreSpringLauncherFactory preSpringLauncherFactory;

  public PreSpringBootstrapApplicationService(PreSpringLauncherFactory preSpringLauncherFactory) {
    Assert.notNull("preSpringLauncherFactory", preSpringLauncherFactory);
    this.preSpringLauncherFactory = preSpringLauncherFactory;
  }

  public int exitCodeFor(PreSpringBootstrapCommand command) {
    Assert.notNull("command", command);
    PreSpringBootstrapCommand sanitizedCommand = command;
    PreSpringLauncher preSpringLauncher = preSpringLauncherFactory.create(
      sanitizedCommand.userHomePath(),
      sanitizedCommand.executablePath(),
      sanitizedCommand.currentSeed4JVersion()
    );
    return preSpringLauncher.launch(sanitizedCommand.args(), sanitizedCommand.childMode());
  }
}
