package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.BootstrapOutput;
import com.seed4j.cli.bootstrap.domain.ConfiguredRuntimeMode;
import com.seed4j.cli.shared.error.domain.Assert;

public class SystemErrBootstrapOutput implements BootstrapOutput {

  private static final String CONFIG_LOCATION = "~/.config/seed4j-cli/config.yml";

  @Override
  public void standardModeFallback() {
    System.err.println("Standard mode is not running from a packaged CLI JAR. Falling back to local execution.");
  }

  @Override
  public void runtimeConfigurationError(String message) {
    System.err.println(message);
  }

  @Override
  public void unsupportedRuntimeMode(ConfiguredRuntimeMode mode) {
    Assert.notNull("mode", mode);
    if (mode.support().retiredExtension()) {
      System.err.println(
        "Loading runtime extension JARs is no longer supported. Set seed4j.runtime.mode: standard in " + CONFIG_LOCATION + "."
      );
      return;
    }

    System.err.println(
      "Invalid %s: Invalid seed4j.runtime.mode '%s'. Valid value: standard.".formatted(CONFIG_LOCATION, mode.value().orElse(""))
    );
  }
}
