package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Optional;

public record RuntimeDisplay(RuntimeModeDisplay mode, Optional<String> distributionId, Optional<String> distributionVersion) {
  public RuntimeDisplay {
    Assert.notNull("mode", mode);
    Assert.notNull("distributionId", distributionId);
    Assert.notNull("distributionVersion", distributionVersion);
  }

  public static RuntimeDisplay standard() {
    return new RuntimeDisplay(RuntimeModeDisplay.STANDARD, Optional.empty(), Optional.empty());
  }

  public static RuntimeDisplay extension(Optional<String> distributionId, Optional<String> distributionVersion) {
    return new RuntimeDisplay(RuntimeModeDisplay.EXTENSION, distributionId, distributionVersion);
  }
}
