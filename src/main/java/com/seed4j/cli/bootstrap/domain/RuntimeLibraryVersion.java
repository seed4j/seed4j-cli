package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeLibraryVersion(String value) {
  public RuntimeLibraryVersion {
    Assert.notBlank("value", value);
  }
}
