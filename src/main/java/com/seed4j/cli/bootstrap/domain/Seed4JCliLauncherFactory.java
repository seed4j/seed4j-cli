package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

public class Seed4JCliLauncherFactory {

  public record LauncherDependencies(Path javaExecutable, ProcessCommandExecutor commandExecutor, LocalCliRunner localCliRunner) {}

  public Seed4JCliLauncher create(
    Path userHome,
    Path executableJar,
    String currentSeed4JVersion,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    LauncherDependencies dependencies
  ) {
    ChildProcessLauncher childProcessLauncher = new JavaProcessChildLauncher(dependencies.javaExecutable(), dependencies.commandExecutor());
    return new Seed4JCliLauncher(
      userHome,
      executableJar,
      currentSeed4JVersion,
      runtimeModeConfigurationRepository,
      childProcessLauncher,
      dependencies.localCliRunner()
    );
  }
}
