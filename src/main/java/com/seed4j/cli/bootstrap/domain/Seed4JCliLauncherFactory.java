package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;
import java.util.List;

public class Seed4JCliLauncherFactory {

  @FunctionalInterface
  public interface CommandExecutor {
    int execute(List<String> command);
  }

  public record LauncherDependencies(
    Path javaExecutable,
    CommandExecutor commandExecutor,
    LocalSpringCliRunner.ApplicationBuilderFactory applicationBuilderFactory,
    LocalSpringCliRunner.ExitCodeResolver exitCodeResolver
  ) {}

  public Seed4JCliLauncher create(
    Path userHome,
    Path executableJar,
    String currentCliVersion,
    String currentSeed4JVersion,
    LauncherDependencies dependencies
  ) {
    LocalSpringCliRunner localCliRunner = new LocalSpringCliRunner(
      dependencies.applicationBuilderFactory(),
      dependencies.exitCodeResolver(),
      () -> userHome
    );
    ChildProcessLauncher childProcessLauncher = new JavaProcessChildLauncher(
      dependencies.javaExecutable(),
      dependencies.commandExecutor()::execute
    );
    return new Seed4JCliLauncher(userHome, executableJar, currentCliVersion, currentSeed4JVersion, childProcessLauncher, localCliRunner);
  }
}
