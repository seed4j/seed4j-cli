package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetIntegerParameterValue(Integer value) implements ModuleSetParameterValue {
  public ModuleSetIntegerParameterValue {
    Assert.notNull("value", value);
  }

  @Override
  public ModuleSetPropertyType type() {
    return ModuleSetPropertyType.INTEGER;
  }
}
