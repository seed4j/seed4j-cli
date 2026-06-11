package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeExtensionCacheIdentity(String value) {
  public RuntimeExtensionCacheIdentity {
    Assert.notBlank("value", value);
  }
}
