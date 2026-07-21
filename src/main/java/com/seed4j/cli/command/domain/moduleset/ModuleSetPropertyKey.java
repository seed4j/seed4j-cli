package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetPropertyKey(String value) {
  public ModuleSetPropertyKey {
    Assert.notBlank("value", value);
  }
}
