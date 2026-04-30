package com.seed4j.cli.bootstrap.domain;

record RuntimeExtensionCacheIdentity(String value) {
  RuntimeExtensionCacheIdentity {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Runtime extension cache identity cannot be null or blank.");
    }
  }
}
