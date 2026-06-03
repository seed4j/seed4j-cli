package com.seed4j.cli.bootstrap.domain;

public interface BootstrapOutput {
  void standardModeFallback();

  void runtimeConfigurationError(String message);
}
