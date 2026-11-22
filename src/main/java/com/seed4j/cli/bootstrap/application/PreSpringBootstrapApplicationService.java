package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.bootstrap.domain.BootstrapOutput;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLauncher;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.bootstrap.domain.PackagedExecutableDetector;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.RuntimeConfiguration;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.shared.error.domain.Assert;

public class PreSpringBootstrapApplicationService {

  private final PreSpringRuntimeEnvironment runtime;
  private final RuntimeConfiguration runtimeConfiguration;
  private final ChildRuntimeLauncher childRuntimeLauncher;
  private final LocalCliRunner localCliRunner;
  private final PackagedExecutableDetector packagedExecutableDetector;
  private final BootstrapOutput bootstrapOutput;

  public PreSpringBootstrapApplicationService(
    PreSpringRuntimeEnvironment runtime,
    RuntimeConfiguration runtimeConfiguration,
    ChildRuntimeLauncher childRuntimeLauncher,
    LocalCliRunner localCliRunner,
    PackagedExecutableDetector packagedExecutableDetector,
    BootstrapOutput bootstrapOutput
  ) {
    this.runtime = runtime;
    this.runtimeConfiguration = runtimeConfiguration;
    this.childRuntimeLauncher = childRuntimeLauncher;
    this.localCliRunner = localCliRunner;
    this.packagedExecutableDetector = packagedExecutableDetector;
    this.bootstrapOutput = bootstrapOutput;
  }

  public int exitCodeFor(Seed4JCliArguments arguments) {
    Assert.notNull("arguments", arguments);
    try {
      runtimeConfiguration.requireStandardMode();
      if (runtime.processMode().child()) {
        return localCliRunner.run(arguments);
      }
      if (packagedExecutableDetector.packagedExecutable(runtime.executablePath().path())) {
        return childRuntimeLauncher.launch(new ChildRuntimeLaunchRequest(runtime.executablePath(), arguments));
      }

      bootstrapOutput.standardModeFallback();
      return localCliRunner.run(arguments);
    } catch (InvalidRuntimeConfigurationException exception) {
      bootstrapOutput.runtimeConfigurationError(exception.getMessage());
      return 1;
    }
  }
}
