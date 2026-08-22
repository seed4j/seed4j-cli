package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetStringParameterValue(String value) implements ModuleSetParameterValue {
  public ModuleSetStringParameterValue {
    Assert.notNull("value", value);
  }

  @Override
  public ModuleSetPropertyType type() {
    return ModuleSetPropertyType.STRING;
  }
}
