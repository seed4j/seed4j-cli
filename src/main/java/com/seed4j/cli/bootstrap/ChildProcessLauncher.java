package com.seed4j.cli.bootstrap;

@FunctionalInterface
interface ChildProcessLauncher {
  int launch(JavaChildProcessRequest request);
}
