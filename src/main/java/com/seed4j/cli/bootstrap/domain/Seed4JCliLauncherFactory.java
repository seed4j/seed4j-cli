package com.seed4j.cli.bootstrap.domain;

public class Seed4JCliLauncherFactory {

  public record LauncherDependencies(
    ChildProcessLauncher childProcessLauncher,
    LocalCliRunner localCliRunner,
    PackagedExecutableDetector packagedExecutableDetector,
    BootstrapDiagnostics bootstrapDiagnostics,
    BootstrapOutput bootstrapOutput,
    RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver,
    RuntimeExtensionOverlayCache runtimeExtensionOverlayCache,
    RuntimeExtensionLoaderPathResolver runtimeExtensionLoaderPathResolver
  ) {}

  public Seed4JCliLauncher create(
    PreSpringRuntimeEnvironment runtimeEnvironment,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository,
    LauncherDependencies dependencies
  ) {
    return new Seed4JCliLauncher(
      runtimeEnvironment.cliHome(),
      runtimeEnvironment.executablePath(),
      runtimeModeConfigurationRepository,
      runtimeExtensionSelectionRepository,
      dependencies.childProcessLauncher(),
      dependencies.localCliRunner(),
      dependencies.packagedExecutableDetector(),
      dependencies.bootstrapDiagnostics(),
      dependencies.bootstrapOutput(),
      dependencies.runtimeExtensionStartClassResolver(),
      dependencies.runtimeExtensionOverlayCache(),
      dependencies.runtimeExtensionLoaderPathResolver(),
      runtimeEnvironment.childMode()
    );
  }
}
