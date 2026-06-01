package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record RuntimeExtensionJarPath(Path path) {
  public RuntimeExtensionJarPath {
    Assert.notNull("path", path);
    Assert.notBlank("path", path.toString());
  }

  public static RuntimeExtensionJarPath from(String path) {
    Assert.notBlank("path", path);
    return new RuntimeExtensionJarPath(Path.of(path));
  }
}
