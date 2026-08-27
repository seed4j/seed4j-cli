package com.seed4j.cli.command.domain.moduleset;

public enum ModuleSetProjectPathStatus {
  VALID,
  NOT_DIRECTORY,
  NOT_ACCESSIBLE,
  NOT_APPARENTLY_CREATABLE;

  public boolean valid() {
    return this == VALID;
  }
}
