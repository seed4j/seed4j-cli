package com.seed4j.cli.bootstrap.domain;

public class Seed4JCliLauncherFactory {

  public record LauncherDependencies(
    ChildRuntimeLauncher childRuntimeLauncher,
    LocalCliRunner localCliRunner,
    PackagedExecutableDetector packagedExecutableDetector,
    BootstrapDiagnostics bootstrapDiagnostics,
    BootstrapOutput bootstrapOutput
  ) {}

  public Seed4JCliLauncher create(
    PreSpringRuntimeEnvironment runtimeEnvironment,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository,
    LauncherDependencies dependencies
  ) {
    return new Seed4JCliLauncher(
      runtimeEnvironment.executablePath(),
      runtimeModeConfigurationRepository,
      runtimeExtensionSelectionRepository,
      dependencies.childRuntimeLauncher(),
      dependencies.localCliRunner(),
      dependencies.packagedExecutableDetector(),
      dependencies.bootstrapDiagnostics(),
      dependencies.bootstrapOutput(),
      runtimeEnvironment.childMode()
    );
  }
}
