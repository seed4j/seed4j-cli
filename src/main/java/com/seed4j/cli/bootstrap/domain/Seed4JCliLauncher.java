package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Seed4JCliLauncher {

  private static final String PROPERTIES_LAUNCHER_MAIN_CLASS = "org.springframework.boot.loader.launch.PropertiesLauncher";

  private final Path userHome;
  private final Path executableJar;
  private final String currentCliVersion;
  private final ChildProcessLauncher childProcessLauncher;
  private final LocalCliRunner localCliRunner;
  private final RuntimeModeConfigReader runtimeModeConfigReader;

  public Seed4JCliLauncher(
    Path userHome,
    Path executableJar,
    String currentCliVersion,
    ChildProcessLauncher childProcessLauncher,
    LocalCliRunner localCliRunner
  ) {
    this(userHome, executableJar, currentCliVersion, childProcessLauncher, localCliRunner, new RuntimeModeConfigReader());
  }

  Seed4JCliLauncher(
    Path userHome,
    Path executableJar,
    String currentCliVersion,
    ChildProcessLauncher childProcessLauncher,
    LocalCliRunner localCliRunner,
    RuntimeModeConfigReader runtimeModeConfigReader
  ) {
    this.userHome = userHome;
    this.executableJar = executableJar;
    this.currentCliVersion = currentCliVersion;
    this.childProcessLauncher = childProcessLauncher;
    this.localCliRunner = localCliRunner;
    this.runtimeModeConfigReader = runtimeModeConfigReader;
  }

  public int launch(String[] args) {
    return launch(args, false);
  }

  public int launch(String[] args, boolean childMode) {
    if (childMode) {
      return localCliRunner.run(args);
    }

    try {
      RuntimeSelection runtimeSelection = runtimeSelection();
      failWhenExtensionModeRunsOutsideARegularJar(runtimeSelection);
      if (shouldRunLocally(runtimeSelection)) {
        System.err.println("Standard mode is not running from a packaged CLI JAR. Falling back to local execution.");
        return localCliRunner.run(args);
      }

      return childProcessLauncher.launch(javaChildProcessRequest(runtimeSelection, args));
    } catch (InvalidRuntimeConfigurationException e) {
      return 1;
    }
  }

  private boolean shouldRunLocally(RuntimeSelection runtimeSelection) {
    return runtimeSelection.mode() == RuntimeMode.STANDARD && notRunningFromARegularJar();
  }

  private void failWhenExtensionModeRunsOutsideARegularJar(RuntimeSelection runtimeSelection) {
    if (runtimeSelection.mode() == RuntimeMode.EXTENSION && notRunningFromARegularJar()) {
      throw new InvalidRuntimeConfigurationException("Extension mode requires running the packaged CLI JAR.");
    }
  }

  private boolean notRunningFromARegularJar() {
    return !Files.isRegularFile(executableJar) || !executableJar.getFileName().toString().endsWith(".jar");
  }

  private JavaChildProcessRequest javaChildProcessRequest(RuntimeSelection runtimeSelection, String[] args) {
    Map<String, String> systemProperties = new LinkedHashMap<>();
    systemProperties.put("seed4j.cli.runtime.child", "true");
    systemProperties.put("seed4j.cli.runtime.mode", runtimeSelection.mode().name().toLowerCase());
    runtimeSelection
      .distributionId()
      .ifPresent(distributionId -> systemProperties.put("seed4j.cli.runtime.distribution.id", distributionId));
    runtimeSelection
      .distributionVersion()
      .ifPresent(distributionVersion -> systemProperties.put("seed4j.cli.runtime.distribution.version", distributionVersion));
    runtimeSelection.extensionJarPath().ifPresent(extensionJarPath -> systemProperties.put("loader.path", extensionJarPath.toString()));

    return new JavaChildProcessRequest(
      executableJar,
      PROPERTIES_LAUNCHER_MAIN_CLASS,
      Map.copyOf(systemProperties),
      List.copyOf(Arrays.asList(args)),
      runtimeSelection
    );
  }

  private RuntimeSelection runtimeSelection() {
    if (runtimeModeConfigReader.runtimeMode(userHome) == RuntimeMode.STANDARD) {
      return new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());
    }

    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      RuntimeExtensionConfiguration.withDefaultPaths(userHome)
    );

    return RuntimeSelection.resolve(runtimeConfiguration, currentCliVersion);
  }
}
