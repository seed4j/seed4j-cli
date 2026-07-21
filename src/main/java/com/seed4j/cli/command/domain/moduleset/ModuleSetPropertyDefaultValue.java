package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetPropertyDefaultValue(String value) {
  public ModuleSetPropertyDefaultValue {
    Assert.notBlank("value", value);
  }
}
