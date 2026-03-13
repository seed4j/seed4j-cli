package com.seed4j.cli.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

class Seed4JCliLauncher {

  private static final String PROPERTIES_LAUNCHER_MAIN_CLASS = "org.springframework.boot.loader.launch.PropertiesLauncher";

  private final Path userHome;
  private final Path executableJar;
  private final String currentCliVersion;
  private final ChildProcessLauncher childProcessLauncher;
  private final LocalCliRunner localCliRunner;

  Seed4JCliLauncher(
    Path userHome,
    Path executableJar,
    String currentCliVersion,
    ChildProcessLauncher childProcessLauncher,
    LocalCliRunner localCliRunner
  ) {
    this.userHome = userHome;
    this.executableJar = executableJar;
    this.currentCliVersion = currentCliVersion;
    this.childProcessLauncher = childProcessLauncher;
    this.localCliRunner = localCliRunner;
  }

  int launch(String[] args) {
    return launch(args, false);
  }

  int launch(String[] args, boolean childMode) {
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
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    if (!Files.exists(configPath)) {
      return new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());
    }

    if (runtimeMode(configPath) == RuntimeMode.STANDARD) {
      return new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());
    }

    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      RuntimeExtensionConfiguration.withDefaultPaths(userHome)
    );

    return RuntimeSelection.resolve(runtimeConfiguration, currentCliVersion);
  }

  private RuntimeMode runtimeMode(Path configPath) {
    try {
      Object loadedConfiguration = new Yaml().load(Files.newInputStream(configPath));
      if (!(loadedConfiguration instanceof Map<?, ?> configuration)) {
        throw new InvalidRuntimeConfigurationException("Invalid ~/.config/seed4j-cli.yml: YAML root must be an object.");
      }
      if (configuration.containsKey("seed4j") && !(configuration.get("seed4j") instanceof Map<?, ?>)) {
        throw new InvalidRuntimeConfigurationException("Invalid ~/.config/seed4j-cli.yml: seed4j must be an object.");
      }
      if (!(configuration.get("seed4j") instanceof Map<?, ?> seed4j)) {
        return RuntimeMode.STANDARD;
      }
      if (!(seed4j.get("runtime") instanceof Map<?, ?> runtime)) {
        return RuntimeMode.STANDARD;
      }
      if (runtime.containsKey("mode") && !(runtime.get("mode") instanceof String)) {
        throw new InvalidRuntimeConfigurationException(
          "Invalid ~/.config/seed4j-cli.yml: seed4j.runtime.mode must be a string. Valid values: standard, extension."
        );
      }
      if (!(runtime.get("mode") instanceof String mode)) {
        return RuntimeMode.STANDARD;
      }

      String normalizedMode = mode.trim().toUpperCase();

      try {
        return RuntimeMode.valueOf(normalizedMode);
      } catch (IllegalArgumentException e) {
        throw new InvalidRuntimeConfigurationException(
          "Invalid seed4j.runtime.mode '%s'. Valid values: standard, extension.".formatted(mode)
        );
      }
    } catch (IOException | YAMLException e) {
      throw new InvalidRuntimeConfigurationException("Could not read ~/.config/seed4j-cli.yml.");
    }
  }
}
