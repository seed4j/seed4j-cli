package com.seed4j.cli.bootstrap.domain;

import java.util.List;

@FunctionalInterface
public interface ProcessCommandExecutor {
  int execute(List<String> command);
}
