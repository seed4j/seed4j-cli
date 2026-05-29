package com.seed4j.cli.bootstrap.infrastructure.primary;

import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapCommand;
import com.seed4j.cli.bootstrap.application.PreSpringLauncherFactory;
import com.seed4j.cli.bootstrap.application.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.composition.InfrastructurePreSpringLauncherFactory;
import com.seed4j.cli.shared.error.domain.Assert;

public class PreSpringLauncherAssembler {

  private final PreSpringLauncherFactory preSpringLauncherFactory;

  public PreSpringLauncherAssembler() {
    this(new InfrastructurePreSpringLauncherFactory());
  }

  PreSpringLauncherAssembler(PreSpringLauncherFactory preSpringLauncherFactory) {
    Assert.notNull("preSpringLauncherFactory", preSpringLauncherFactory);
    this.preSpringLauncherFactory = preSpringLauncherFactory;
  }

  public int exitCodeFor(PreSpringRuntimeEnvironment runtimeEnvironment, String[] args) {
    Assert.notNull("runtimeEnvironment", runtimeEnvironment);
    PreSpringBootstrapApplicationService preSpringBootstrapApplicationService = new PreSpringBootstrapApplicationService(
      preSpringLauncherFactory,
      () -> runtimeEnvironment
    );
    PreSpringBootstrapCommand command = new PreSpringBootstrapCommand(args);
    return preSpringBootstrapApplicationService.exitCodeFor(command);
  }
}
