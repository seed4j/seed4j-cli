package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetExplicitParameterTypeMismatch(
  ModuleSetPropertyKey key,
  ModuleSetPropertyType expectedType,
  ModuleSetPropertyType actualType
) implements ModuleSetPlanningProblem {
  public ModuleSetExplicitParameterTypeMismatch {
    Assert.notNull("key", key);
    Assert.notNull("expectedType", expectedType);
    Assert.notNull("actualType", actualType);
  }
}
