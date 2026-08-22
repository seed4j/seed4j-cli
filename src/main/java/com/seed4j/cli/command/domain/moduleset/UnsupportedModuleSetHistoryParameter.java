package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record UnsupportedModuleSetHistoryParameter(ModuleSetPropertyKey key) {
  public UnsupportedModuleSetHistoryParameter {
    Assert.notNull("key", key);
  }
}
