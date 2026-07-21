package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record ModuleSetProjectPath(Path value) {
  public ModuleSetProjectPath {
    Assert.notNull("value", value);
  }
}
