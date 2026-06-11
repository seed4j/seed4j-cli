package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record RuntimeModeConfigurationPath(Path path) {
  public RuntimeModeConfigurationPath {
    Assert.notNull("path", path);
  }
}
