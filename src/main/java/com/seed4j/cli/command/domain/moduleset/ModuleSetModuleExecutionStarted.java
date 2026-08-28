package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetModuleExecutionStarted(ModuleSetPlanItem item) implements ModuleSetExecutionEvent {
  public ModuleSetModuleExecutionStarted {
    Assert.notNull("item", item);
  }
}
