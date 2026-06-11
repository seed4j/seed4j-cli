package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeExtensionJarPath(String value) {
  public RuntimeExtensionJarPath {
    Assert.notBlank("value", value);
  }
}
