package com.seed4j.cli.bootstrap.domain;

public enum RuntimeModeSupport {
  SUPPORTED,
  RETIRED_EXTENSION,
  UNRECOGNIZED;

  public boolean unsupported() {
    return this != SUPPORTED;
  }

  public boolean retiredExtension() {
    return this == RETIRED_EXTENSION;
  }
}
