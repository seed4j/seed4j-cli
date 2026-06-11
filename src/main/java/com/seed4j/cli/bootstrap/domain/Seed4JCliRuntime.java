package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

public interface Seed4JCliRuntime {
  Path executableJar();

  boolean childRuntime();
}
