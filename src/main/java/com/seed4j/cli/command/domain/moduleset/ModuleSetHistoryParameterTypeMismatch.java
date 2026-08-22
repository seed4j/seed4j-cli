package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetHistoryParameterTypeMismatch(
  ModuleSetPropertyKey key,
  ModuleSetPropertyType expectedType,
  ModuleSetHistoryParameterValueType historyType
) implements ModuleSetPlanningProblem {
  public ModuleSetHistoryParameterTypeMismatch {
    Assert.notNull("key", key);
    Assert.notNull("expectedType", expectedType);
    Assert.notNull("historyType", historyType);
  }
}
