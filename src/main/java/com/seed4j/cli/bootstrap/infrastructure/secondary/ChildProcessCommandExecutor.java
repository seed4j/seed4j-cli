package com.seed4j.cli.bootstrap.infrastructure.secondary;

import java.util.List;

@FunctionalInterface
public interface ChildProcessCommandExecutor {
  int execute(List<String> command);
}
