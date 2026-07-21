package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetPropertyDescription(String value) {
  public ModuleSetPropertyDescription {
    Assert.notBlank("value", value);
  }
}
