package com.seed4j.cli.bootstrap.composition;

import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionSelectionRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncher;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRunner;
import com.seed4j.cli.bootstrap.infrastructure.secondary.CurrentProcessPreSpringRuntimeEnvironmentReader;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionSelectionRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JarRuntimeExtensionPackageValidator;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JavaChildProcessCommandExecutor;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JavaProcessChildLauncher;
import com.seed4j.cli.bootstrap.infrastructure.secondary.LogbackBootstrapDiagnostics;
import com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionLoaderPathResolver;
import com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionOverlayCache;
import com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionStartClassResolver;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SpringBootLocalCliRunner;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput;
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
    LocalCliRunner localCliRunner = new SpringBootLocalCliRunner(Seed4JCliApp.class, runtimeEnvironment.cliHome());
    RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository = new FileSystemRuntimeExtensionSelectionRepository(
      runtimeEnvironment.cliHome(),
      new JarRuntimeExtensionPackageValidator()
    );
    return new Seed4JCliLauncher(
      runtimeEnvironment.executablePath(),
      (RuntimeModeConfigurationRepository) new FileSystemRuntimeModeConfigurationRepository(runtimeEnvironment.cliHome()),
      runtimeExtensionSelectionRepository,
      new JavaProcessChildLauncher(
        runtimeEnvironment.javaExecutablePath(),
        new JavaChildProcessCommandExecutor(),
        new RuntimeExtensionStartClassResolver(),
        new RuntimeExtensionOverlayCache(runtimeEnvironment.cliHome()),
        new RuntimeExtensionLoaderPathResolver()
      ),
      localCliRunner,
      new FileSystemPackagedExecutableDetector(),
      new LogbackBootstrapDiagnostics(),
      new SystemErrBootstrapOutput(),
      runtimeEnvironment.childMode()
    );
  }
}
