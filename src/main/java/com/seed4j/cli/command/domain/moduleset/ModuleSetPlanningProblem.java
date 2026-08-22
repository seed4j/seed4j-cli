package com.seed4j.cli.command.domain.moduleset;

public sealed interface ModuleSetPlanningProblem
  permits
    DuplicateRequestedModuleSetModules,
    UnknownRequestedModuleSetModules,
    ModuleSetPropertyConflicts,
    ModuleSetHistoryParameterTypeMismatch,
    UnusedExplicitModuleSetParameters {}
