package com.seed4j.cli.command.domain.moduleset;

public sealed interface ModuleSetExecutionEvent permits ModuleSetModuleExecutionStarted, ModuleSetModuleExecutionCompleted {}
