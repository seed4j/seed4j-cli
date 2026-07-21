package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ResolvedModuleSetParameter(
  ModuleSetPropertyKey key,
  Object value,
  ModuleSetPropertySource source,
  ModuleSetPropertyDefinition definition
) {
  public ResolvedModuleSetParameter {
    Assert.notNull("key", key);
    Assert.notNull("value", value);
    Assert.notNull("source", source);
    Assert.notNull("definition", definition);
  }
}
