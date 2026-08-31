package com.seed4j.cli.command.domain.moduleset;

public enum ModuleSetDetailedPlanningStatus {
  EVALUATED,
  NOT_EVALUATED;

  public boolean notEvaluated() {
    return this == NOT_EVALUATED;
  }
}
