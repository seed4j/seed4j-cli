package com.seed4j.cli.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

class Seed4JCliLauncher {

  private final Path userHome;
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

      return childProcessLauncher.launch(runtimeSelection, args);
    } catch (InvalidRuntimeConfigurationException e) {
      return 1;
    }
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
