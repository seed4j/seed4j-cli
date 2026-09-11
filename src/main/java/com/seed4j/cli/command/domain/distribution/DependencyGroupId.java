package com.seed4j.cli.command.domain.distribution;

import com.seed4j.cli.shared.error.domain.Assert;

public record DependencyGroupId(String value) {
  public DependencyGroupId {
    Assert.notBlank("value", value);
  }
}
