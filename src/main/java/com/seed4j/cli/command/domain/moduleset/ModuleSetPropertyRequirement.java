package com.seed4j.cli.command.domain.moduleset;

public enum ModuleSetPropertyRequirement {
  REQUIRED,
  OPTIONAL;

  public boolean mandatory() {
    return this == REQUIRED;
  }
}
