package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeDistributionId(String value) {
  public RuntimeDistributionId {
    Assert.notBlank("value", value);
  }
}
