package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record Seed4JCliExecutablePath(Path path) {
  public Seed4JCliExecutablePath {
    Assert.notNull("path", path);
  }
}
