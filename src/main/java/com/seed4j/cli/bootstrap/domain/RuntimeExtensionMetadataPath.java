package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record RuntimeExtensionMetadataPath(Path path) {
  public RuntimeExtensionMetadataPath {
    Assert.notNull("path", path);
  }
}
