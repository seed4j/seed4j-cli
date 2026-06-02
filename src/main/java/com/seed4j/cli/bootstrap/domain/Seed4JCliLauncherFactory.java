package com.seed4j.cli.bootstrap.domain;

public class Seed4JCliLauncherFactory {

  public record LauncherDependencies(ProcessCommandExecutor commandExecutor, LocalCliRunner localCliRunner) {}

  public Seed4JCliLauncher create(
    PreSpringRuntimeEnvironment runtimeEnvironment,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    LauncherDependencies dependencies
  ) {
    ChildProcessLauncher childProcessLauncher = new JavaProcessChildLauncher(
      runtimeEnvironment.javaExecutablePath(),
      dependencies.commandExecutor()
    );
    return new Seed4JCliLauncher(
      runtimeEnvironment.cliHome(),
      runtimeEnvironment.executablePath(),
      runtimeModeConfigurationRepository,
      childProcessLauncher,
      dependencies.localCliRunner(),
      runtimeEnvironment.childMode()
    );
  }
}
