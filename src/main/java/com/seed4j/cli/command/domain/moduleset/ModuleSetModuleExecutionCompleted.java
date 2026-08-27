package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetModuleExecutionCompleted(ModuleSetPlanItem item, ModuleSetModuleStatus status) implements ModuleSetExecutionEvent {
  public ModuleSetModuleExecutionCompleted {
    Assert.notNull("item", item);
    Assert.notNull("status", status);
  }
}
