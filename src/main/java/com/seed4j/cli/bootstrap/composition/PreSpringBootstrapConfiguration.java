package com.seed4j.cli.bootstrap.composition;

import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncher;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncherFactory;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRunner;
import com.seed4j.cli.bootstrap.infrastructure.secondary.CurrentProcessPreSpringRuntimeEnvironmentReader;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JavaChildProcessCommandExecutor;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SpringBootLocalCliRunner;
import com.seed4j.cli.shared.error.domain.Assert;

public final class PreSpringBootstrapConfiguration {

  private PreSpringBootstrapConfiguration() {}

  public static PreSpringBootstrapRunner preSpringBootstrapRunner() {
    CurrentProcessPreSpringRuntimeEnvironmentReader preSpringRuntimeEnvironmentReader =
      new CurrentProcessPreSpringRuntimeEnvironmentReader();
    PreSpringRuntimeEnvironment runtimeEnvironment = preSpringRuntimeEnvironmentReader.current();
    return preSpringBootstrapRunner(runtimeEnvironment);
  }

  static PreSpringBootstrapRunner preSpringBootstrapRunner(PreSpringRuntimeEnvironment runtimeEnvironment) {
    Assert.notNull("runtimeEnvironment", runtimeEnvironment);
    PreSpringBootstrapApplicationService preSpringBootstrapApplicationService = new PreSpringBootstrapApplicationService(
      seed4jCliLauncher(runtimeEnvironment)
    );
    return new PreSpringBootstrapRunner(preSpringBootstrapApplicationService);
  }

  private static Seed4JCliLauncher seed4jCliLauncher(PreSpringRuntimeEnvironment runtimeEnvironment) {
    Seed4JCliLauncherFactory launcherFactory = new Seed4JCliLauncherFactory();
    LocalCliRunner localCliRunner = new SpringBootLocalCliRunner(Seed4JCliApp.class, runtimeEnvironment.cliHome());
    Seed4JCliLauncherFactory.LauncherDependencies launcherDependencies = new Seed4JCliLauncherFactory.LauncherDependencies(
      new JavaChildProcessCommandExecutor(),
      localCliRunner
    );
    return launcherFactory.create(
      runtimeEnvironment,
      new FileSystemRuntimeModeConfigurationRepository(runtimeEnvironment.cliHome()),
      launcherDependencies
    );
  }
}
