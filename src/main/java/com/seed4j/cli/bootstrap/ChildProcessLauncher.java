package com.seed4j.cli.bootstrap;

@FunctionalInterface
interface ChildProcessLauncher {
  int launch(RuntimeSelection runtimeSelection, String[] args);
}
