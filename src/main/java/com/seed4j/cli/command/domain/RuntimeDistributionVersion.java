package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeDistributionVersion(String value) {
  public RuntimeDistributionVersion {
    Assert.notBlank("value", value);
  }
}
