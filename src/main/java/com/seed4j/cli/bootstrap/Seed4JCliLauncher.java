package com.seed4j.cli.bootstrap;

import java.nio.file.Path;
import java.util.Optional;

class Seed4JCliLauncher {

  private final ChildProcessLauncher childProcessLauncher;

  Seed4JCliLauncher(Path userHome, Path executableJar, String currentCliVersion, ChildProcessLauncher childProcessLauncher) {
    this.childProcessLauncher = childProcessLauncher;
  }

  int launch(String[] args) {
    RuntimeSelection runtimeSelection = new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());

    return childProcessLauncher.launch(runtimeSelection, args);
  }
}
