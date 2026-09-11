package com.seed4j.cli.command.domain.distribution;

public enum ReleaseChannel {
  STABLE,
  EXPERIMENTAL;

  public boolean experimental() {
    return this == EXPERIMENTAL;
  }
}
