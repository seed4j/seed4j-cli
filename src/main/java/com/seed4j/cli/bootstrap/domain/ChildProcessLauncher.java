package com.seed4j.cli.bootstrap.domain;

@FunctionalInterface
public interface ChildProcessLauncher {
  int launch(JavaChildProcessRequest request);
}
