package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record RuntimeExtensionModeSwitchResult(Path configPath) {
  public RuntimeExtensionModeSwitchResult {
    Assert.notNull("configPath", configPath);
  }
}
