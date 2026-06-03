package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.BootstrapOutput;

public class SystemErrBootstrapOutput implements BootstrapOutput {

  @Override
  public void standardModeFallback() {
    System.err.println("Standard mode is not running from a packaged CLI JAR. Falling back to local execution.");
  }

  @Override
  public void runtimeConfigurationError(String message) {
    System.err.println(message);
  }
}
