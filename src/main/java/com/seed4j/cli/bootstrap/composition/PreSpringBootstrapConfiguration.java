package com.seed4j.cli.bootstrap.composition;

import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.application.PreSpringLauncher;
import com.seed4j.cli.bootstrap.application.PreSpringLauncherFactory;
import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironmentReader;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncher;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncherFactory;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRunner;
import com.seed4j.cli.bootstrap.infrastructure.secondary.CurrentProcessPreSpringRuntimeEnvironmentReader;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JavaChildProcessCommandExecutor;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SpringBootLocalCliRunner;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public final class PreSpringBootstrapConfiguration {

  private PreSpringBootstrapConfiguration() {}

  public static PreSpringBootstrapRunner preSpringBootstrapRunner() {
    return preSpringBootstrapRunner(new CurrentProcessPreSpringRuntimeEnvironmentReader());
  }

  static PreSpringBootstrapRunner preSpringBootstrapRunner(PreSpringRuntimeEnvironmentReader preSpringRuntimeEnvironmentReader) {
    Assert.notNull("preSpringRuntimeEnvironmentReader", preSpringRuntimeEnvironmentReader);
    PreSpringBootstrapApplicationService preSpringBootstrapApplicationService = new PreSpringBootstrapApplicationService(
      preSpringLauncherFactory(),
      preSpringRuntimeEnvironmentReader
    );
    return new PreSpringBootstrapRunner(preSpringBootstrapApplicationService);
  }

  private static PreSpringLauncherFactory preSpringLauncherFactory() {
    return PreSpringBootstrapConfiguration::preSpringLauncher;
  }

  private static PreSpringLauncher preSpringLauncher(Path userHomePath, Path executablePath, Path javaExecutablePath) {
    Seed4JCliLauncherFactory launcherFactory = new Seed4JCliLauncherFactory();
    LocalCliRunner localCliRunner = new SpringBootLocalCliRunner(Seed4JCliApp.class, userHomePath);
    Seed4JCliLauncherFactory.LauncherDependencies launcherDependencies = new Seed4JCliLauncherFactory.LauncherDependencies(
      javaExecutablePath,
      new JavaChildProcessCommandExecutor(),
      localCliRunner
    );
    Seed4JCliLauncher launcher = launcherFactory.create(
      userHomePath,
      executablePath,
      new FileSystemRuntimeModeConfigurationRepository(userHomePath),
      launcherDependencies
    );
    return launcher::launch;
  }
}
