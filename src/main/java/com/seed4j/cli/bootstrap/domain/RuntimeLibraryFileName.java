package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeLibraryFileName(String value) {
  public RuntimeLibraryFileName {
    Assert.notBlank("value", value);
  }
}
