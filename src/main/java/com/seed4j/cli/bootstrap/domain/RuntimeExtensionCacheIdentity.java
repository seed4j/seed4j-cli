package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

record RuntimeExtensionCacheIdentity(String value) {
  RuntimeExtensionCacheIdentity {
    Assert.notBlank("value", value);
  }
}
