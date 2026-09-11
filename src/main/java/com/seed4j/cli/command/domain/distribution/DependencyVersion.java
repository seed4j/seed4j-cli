package com.seed4j.cli.command.domain.distribution;

import com.seed4j.cli.shared.error.domain.Assert;

public record DependencyVersion(String value) {
  public DependencyVersion {
    Assert.notBlank("value", value);
  }
}
