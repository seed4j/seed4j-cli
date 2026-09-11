package com.seed4j.cli.command.domain.distribution;

import com.seed4j.cli.shared.error.domain.Assert;

public record Seed4JUpstreamCommit(String value) {
  public Seed4JUpstreamCommit {
    Assert.notBlank("value", value);
    if (!value.matches("[0-9a-f]{40}")) {
      throw new IllegalArgumentException("Seed4J upstream commit must be a 40-character lowercase hexadecimal SHA");
    }
  }
}
