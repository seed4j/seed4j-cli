package com.seed4j.cli.bootstrap;

import java.nio.file.Path;
import java.util.Optional;

class Seed4JCliLauncher {

  private final ChildProcessLauncher childProcessLauncher;
  private final LocalCliRunner localCliRunner;

  Seed4JCliLauncher(
    Path userHome,
    Path executableJar,
    String currentCliVersion,
    ChildProcessLauncher childProcessLauncher,
    LocalCliRunner localCliRunner
  ) {
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

    RuntimeSelection runtimeSelection = new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());

    return childProcessLauncher.launch(runtimeSelection, args);
  }
}
