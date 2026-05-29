package com.seed4j.cli.bootstrap.domain;

@FunctionalInterface
public interface PreSpringRuntimeEnvironmentReader {
  PreSpringRuntimeEnvironment current();
}
