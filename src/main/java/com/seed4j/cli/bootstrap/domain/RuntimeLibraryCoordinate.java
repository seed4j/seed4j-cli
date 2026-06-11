package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeLibraryCoordinate(String value) {
  public RuntimeLibraryCoordinate {
    Assert.notBlank("value", value);
  }
}
