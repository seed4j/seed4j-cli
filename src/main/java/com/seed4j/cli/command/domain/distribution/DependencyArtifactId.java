package com.seed4j.cli.command.domain.distribution;

import com.seed4j.cli.shared.error.domain.Assert;

public record DependencyArtifactId(String value) {
  public DependencyArtifactId {
    Assert.notBlank("value", value);
  }
}
