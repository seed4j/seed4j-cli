package com.seed4j.cli.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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

    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      RuntimeExtensionConfiguration.withDefaultPaths(userHome)
    );

    return RuntimeSelection.resolve(runtimeConfiguration, currentCliVersion);
  }
}
