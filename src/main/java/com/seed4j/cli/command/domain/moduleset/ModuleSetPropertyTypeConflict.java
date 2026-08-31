package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record ModuleSetPropertyTypeConflict(
  ModuleSetPropertyKey key,
  List<ModuleSetPropertyType> types
) implements ModuleSetPropertyConflict {
  public ModuleSetPropertyTypeConflict {
    Assert.notNull("key", key);
    Assert.notNull("types", types);
    types = types.stream().distinct().sorted().toList();
  }
}
