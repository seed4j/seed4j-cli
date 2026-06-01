package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeDistributionVersion(String version) {
  public RuntimeDistributionVersion {
    Assert.notBlank("version", version);
  }
}
