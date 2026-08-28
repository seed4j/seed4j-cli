package com.seed4j.cli.command.domain.moduleset;

public enum ModuleSetApplicationKind {
  APPLICATION,
  REAPPLICATION;

  public boolean reapplied() {
    return this == REAPPLICATION;
  }
}
