package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetBooleanParameterValue(Boolean value) implements ModuleSetParameterValue {
  public ModuleSetBooleanParameterValue {
    Assert.notNull("value", value);
  }

  @Override
  public ModuleSetPropertyType type() {
    return ModuleSetPropertyType.BOOLEAN;
  }
}
