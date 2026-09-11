package com.seed4j.cli.command.domain.moduleset;

public sealed interface ModuleSetPlanningProblem
  permits
    DuplicateRequestedModuleSetModules,
    UnavailableRequestedModuleSetModules,
    InvalidModuleSetProjectPath,
    ModuleSetExecutionOrderMismatch,
    UnknownRequestedModuleSetModules,
    ModuleSetPropertyConflicts,
    ModuleSetHistoryParameterTypeMismatch,
    UnusedExplicitModuleSetParameters {}
