package com.seed4j.cli.command.domain.distribution;

import com.seed4j.cli.shared.error.domain.Assert;

public record DistributionModuleSlug(String value) {
  public DistributionModuleSlug {
    Assert.notBlank("value", value);
  }
}
