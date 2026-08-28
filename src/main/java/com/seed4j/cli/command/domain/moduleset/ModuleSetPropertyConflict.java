package com.seed4j.cli.command.domain.moduleset;

public sealed interface ModuleSetPropertyConflict
  permits ModuleSetPropertyDefaultConflict, ModuleSetPropertyDescriptionConflict, ModuleSetPropertyTypeConflict {}
