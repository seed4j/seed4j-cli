package com.seed4j.cli.command.domain.moduleset;

public enum ModuleSetCommitMode {
  ENABLED,
  DISABLED;

  public boolean enabled() {
    return this == ENABLED;
  }

  public boolean disabled() {
    return this == DISABLED;
  }
}
