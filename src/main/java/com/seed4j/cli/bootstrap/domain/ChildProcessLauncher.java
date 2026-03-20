package com.seed4j.cli.bootstrap.domain;

@FunctionalInterface
interface ChildProcessLauncher {
  int launch(JavaChildProcessRequest request);
}
