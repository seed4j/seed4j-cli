package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Optional;

public record ConfiguredRuntimeMode(Optional<String> value) {
  private static final String STANDARD = "standard";
  private static final String EXTENSION = "extension";

  public ConfiguredRuntimeMode {
    Assert.notNull("value", value);
  }

  public static ConfiguredRuntimeMode notConfigured() {
    return new ConfiguredRuntimeMode(Optional.empty());
  }

  public static ConfiguredRuntimeMode configured(String value) {
    Assert.notNull("value", value);
    return new ConfiguredRuntimeMode(Optional.of(value));
  }

  public RuntimeModeSupport support() {
    return value.map(String::trim).map(ConfiguredRuntimeMode::supportOf).orElse(RuntimeModeSupport.SUPPORTED);
  }

  private static RuntimeModeSupport supportOf(String mode) {
    if (STANDARD.equalsIgnoreCase(mode)) {
      return RuntimeModeSupport.SUPPORTED;
    }
    if (EXTENSION.equalsIgnoreCase(mode)) {
      return RuntimeModeSupport.RETIRED_EXTENSION;
    }
    return RuntimeModeSupport.UNRECOGNIZED;
  }
}
