package com.seed4j.cli.bootstrap.composition;

import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRunner;
import com.seed4j.cli.bootstrap.infrastructure.secondary.CurrentProcessPreSpringRuntimeEnvironmentReader;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeConfiguration;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JavaChildProcessCommandExecutor;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JavaProcessChildLauncher;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SpringBootLocalCliRunner;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput;
import com.seed4j.cli.shared.error.domain.Assert;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;

public final class PreSpringBootstrapConfiguration {

  private PreSpringBootstrapConfiguration() {}

  @ExcludeFromGeneratedCodeCoverage(
    reason = "Production bootstrap entry point reads current process environment and delegates to explicitly tested configuration"
  )
  public static PreSpringBootstrapRunner preSpringBootstrapRunner() {
    CurrentProcessPreSpringRuntimeEnvironmentReader preSpringRuntimeEnvironmentReader =
      new CurrentProcessPreSpringRuntimeEnvironmentReader();
    PreSpringRuntimeEnvironment runtimeEnvironment = preSpringRuntimeEnvironmentReader.current();
    return preSpringBootstrapRunner(runtimeEnvironment);
  }

  static PreSpringBootstrapRunner preSpringBootstrapRunner(PreSpringRuntimeEnvironment runtimeEnvironment) {
    Assert.notNull("runtimeEnvironment", runtimeEnvironment);
    PreSpringBootstrapApplicationService preSpringBootstrapApplicationService = new PreSpringBootstrapApplicationService(
      runtimeEnvironment,
      new FileSystemRuntimeConfiguration(runtimeEnvironment.cliHome()),
      new JavaProcessChildLauncher(
        runtimeEnvironment.javaExecutablePath(),
        runtimeEnvironment.cliHome(),
        new JavaChildProcessCommandExecutor()
      ),
      new SpringBootLocalCliRunner(Seed4JCliApp.class, runtimeEnvironment.cliHome()),
      new FileSystemPackagedExecutableDetector(),
      new SystemErrBootstrapOutput()
    );
    return new PreSpringBootstrapRunner(preSpringBootstrapApplicationService);
  }
}
