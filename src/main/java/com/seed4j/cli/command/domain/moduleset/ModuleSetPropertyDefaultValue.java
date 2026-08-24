package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetPropertyDefaultValue(ModuleSetParameterValue value, String literal) {
  public ModuleSetPropertyDefaultValue {
    Assert.notNull("value", value);
    Assert.notBlank("literal", literal);
  }
}
