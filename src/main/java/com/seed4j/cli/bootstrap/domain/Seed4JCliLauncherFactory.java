package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

public class Seed4JCliLauncherFactory {

  public record LauncherDependencies(Path javaExecutable, ProcessCommandExecutor commandExecutor, LocalCliRunner localCliRunner) {}

  public Seed4JCliLauncher create(
    Path userHome,
    Path executableJar,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    LauncherDependencies dependencies
  ) {
    ChildProcessLauncher childProcessLauncher = new JavaProcessChildLauncher(dependencies.javaExecutable(), dependencies.commandExecutor());
    return new Seed4JCliLauncher(
      userHome,
      executableJar,
      runtimeModeConfigurationRepository,
      childProcessLauncher,
      dependencies.localCliRunner()
    );
  }
}
