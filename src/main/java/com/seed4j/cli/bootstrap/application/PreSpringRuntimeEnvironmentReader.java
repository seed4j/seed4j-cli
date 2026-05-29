package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;

@FunctionalInterface
public interface PreSpringRuntimeEnvironmentReader {
  PreSpringRuntimeEnvironment current();
}
