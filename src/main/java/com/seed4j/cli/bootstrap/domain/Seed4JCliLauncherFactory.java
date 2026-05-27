package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
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

  public Seed4JCliLauncher create(Path userHome, Path executableJar, String currentSeed4JVersion, LauncherDependencies dependencies) {
    LocalSpringCliRunner localCliRunner = new LocalSpringCliRunner(
      dependencies.applicationBuilderFactory(),
      dependencies.exitCodeResolver(),
      () -> userHome
    );
    ChildProcessLauncher childProcessLauncher = new JavaProcessChildLauncher(
      dependencies.javaExecutable(),
      dependencies.commandExecutor()::execute
    );
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository = new FileSystemRuntimeModeConfigurationRepository(userHome);
    return new Seed4JCliLauncher(
      userHome,
      executableJar,
      currentSeed4JVersion,
      runtimeModeConfigurationRepository,
      childProcessLauncher,
      localCliRunner
    );
  }
}
