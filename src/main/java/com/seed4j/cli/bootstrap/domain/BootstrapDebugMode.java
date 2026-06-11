package com.seed4j.cli.bootstrap.domain;

public enum BootstrapDebugMode {
  DISABLED,
  ENABLED;

  public static BootstrapDebugMode from(boolean enabled) {
    return enabled ? ENABLED : DISABLED;
  }

  public boolean enabled() {
    return this == ENABLED;
  }
}
