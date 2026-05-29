package com.seed4j.cli.bootstrap.infrastructure.primary;

import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapCommand;
import com.seed4j.cli.bootstrap.application.PreSpringLauncherFactory;
import com.seed4j.cli.bootstrap.infrastructure.composition.InfrastructurePreSpringLauncherFactory;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public class PreSpringLauncherAssembler {

  private final PreSpringLauncherFactory preSpringLauncherFactory;

  public PreSpringLauncherAssembler() {
    this(new InfrastructurePreSpringLauncherFactory());
  }

  PreSpringLauncherAssembler(PreSpringLauncherFactory preSpringLauncherFactory) {
    Assert.notNull("preSpringLauncherFactory", preSpringLauncherFactory);
    this.preSpringLauncherFactory = preSpringLauncherFactory;
  }

  public int exitCodeFor(Path userHomePath, Path executablePath, String currentSeed4JVersion, boolean childMode, String[] args) {
    PreSpringBootstrapApplicationService preSpringBootstrapApplicationService = new PreSpringBootstrapApplicationService(
      preSpringLauncherFactory
    );
    PreSpringBootstrapCommand command = new PreSpringBootstrapCommand(userHomePath, executablePath, currentSeed4JVersion, childMode, args);
    return preSpringBootstrapApplicationService.exitCodeFor(command);
  }
}
