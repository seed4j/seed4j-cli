package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record ModuleSetPropertyDefaultConflict(
  ModuleSetPropertyKey key,
  List<ModuleSetPropertyDefaultValue> defaults
) implements ModuleSetPropertyConflict {
  public ModuleSetPropertyDefaultConflict {
    Assert.notNull("key", key);
    Assert.notNull("defaults", defaults);
    defaults = List.copyOf(defaults);
  }
}
