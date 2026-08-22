package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record ModuleSetPropertyDescriptionConflict(
  ModuleSetPropertyKey key,
  List<ModuleSetPropertyDescription> descriptions
) implements ModuleSetPropertyConflict {
  public ModuleSetPropertyDescriptionConflict {
    Assert.notNull("key", key);
    Assert.notNull("descriptions", descriptions);
    descriptions = List.copyOf(descriptions);
  }
}
