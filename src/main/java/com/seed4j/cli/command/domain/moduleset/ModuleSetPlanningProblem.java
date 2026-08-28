package com.seed4j.cli.command.domain.moduleset;

public sealed interface ModuleSetPlanningProblem
  permits
    DuplicateRequestedModuleSetModules,
    InvalidModuleSetProjectPath,
    ModuleSetExecutionOrderMismatch,
    UnknownRequestedModuleSetModules,
    ModuleSetPropertyConflicts,
    ModuleSetHistoryParameterTypeMismatch,
    UnusedExplicitModuleSetParameters {}
