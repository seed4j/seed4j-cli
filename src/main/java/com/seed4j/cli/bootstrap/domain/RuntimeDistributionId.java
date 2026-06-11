package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeDistributionId(String id) {
  public RuntimeDistributionId {
    Assert.notBlank("id", id);
  }
}
