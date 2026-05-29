package com.seed4j.cli.bootstrap.application;

@FunctionalInterface
public interface PreSpringRuntimeEnvironmentReader {
  PreSpringRuntimeEnvironment current();
}
