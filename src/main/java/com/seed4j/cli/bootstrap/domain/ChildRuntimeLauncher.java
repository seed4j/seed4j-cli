package com.seed4j.cli.bootstrap.domain;

@FunctionalInterface
public interface ChildRuntimeLauncher {
  int launch(ChildRuntimeLaunchRequest request);
}
