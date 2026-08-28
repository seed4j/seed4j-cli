package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetModuleResult(ModuleSetPlanItem item, ModuleSetModuleStatus status) {
  public ModuleSetModuleResult {
    Assert.notNull("item", item);
    Assert.notNull("status", status);
  }
}
