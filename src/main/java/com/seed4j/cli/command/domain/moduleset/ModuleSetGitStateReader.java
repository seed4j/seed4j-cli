package com.seed4j.cli.command.domain.moduleset;

public interface ModuleSetGitStateReader {
  ModuleSetGitState state(ModuleSetProjectPath projectPath);
}
