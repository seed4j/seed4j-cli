package com.seed4j.cli.command.domain.moduleset;

public interface ModuleSetPlanningHistoryReader {
  ModuleSetPlanningHistory history(ModuleSetProjectPath projectPath);
}
