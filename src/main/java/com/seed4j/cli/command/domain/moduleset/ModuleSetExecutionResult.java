package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record ModuleSetExecutionResult(List<ModuleSetModuleResult> modules, ModuleSetExecutionStatus status) {
  public ModuleSetExecutionResult {
    Assert.notNull("modules", modules);
    Assert.notNull("status", status);
    modules = List.copyOf(modules);
  }
}
