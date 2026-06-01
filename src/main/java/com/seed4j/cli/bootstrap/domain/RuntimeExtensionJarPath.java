package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record RuntimeExtensionJarPath(String path) {
  public RuntimeExtensionJarPath {
    Assert.notBlank("path", path);
    Path.of(path);
  }

  public Path filePath() {
    return Path.of(path);
  }
}
