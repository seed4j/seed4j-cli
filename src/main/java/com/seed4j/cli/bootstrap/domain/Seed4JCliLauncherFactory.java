package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;
import java.util.List;

public class Seed4JCliLauncherFactory {

  @FunctionalInterface
  public interface CommandExecutor {
    int execute(List<String> command);
  }

  public Seed4JCliLauncher create(
    Path userHome,
    Path executableJar,
    String currentCliVersion,
    String currentSeed4JVersion,
    Path javaExecutable,
    CommandExecutor commandExecutor,
    LocalSpringCliRunner.ApplicationBuilderFactory applicationBuilderFactory,
    LocalSpringCliRunner.ExitCodeResolver exitCodeResolver
  ) {
    LocalSpringCliRunner localCliRunner = new LocalSpringCliRunner(applicationBuilderFactory, exitCodeResolver, () -> userHome);
    ChildProcessLauncher childProcessLauncher = new JavaProcessChildLauncher(javaExecutable, commandExecutor::execute);
    return new Seed4JCliLauncher(userHome, executableJar, currentCliVersion, currentSeed4JVersion, childProcessLauncher, localCliRunner);
  }
}
