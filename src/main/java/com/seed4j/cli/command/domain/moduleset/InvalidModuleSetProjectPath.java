package com.seed4j.cli.command.domain.moduleset;

public enum InvalidModuleSetProjectPath implements ModuleSetPlanningProblem {
  NOT_DIRECTORY,
  NOT_ACCESSIBLE,
  NOT_APPARENTLY_CREATABLE,
}
