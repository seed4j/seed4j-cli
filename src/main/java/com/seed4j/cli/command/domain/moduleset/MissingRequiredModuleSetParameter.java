package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record MissingRequiredModuleSetParameter(ModuleSetPropertyKey key) {
  public MissingRequiredModuleSetParameter {
    Assert.notNull("key", key);
  }
}
