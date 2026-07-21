package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record RequestedModuleSet(List<ModuleSetSlug> modules) {
  public RequestedModuleSet {
    Assert.notNull("modules", modules);
    Assert.notEmpty("modules", modules);
    modules = List.copyOf(modules);
  }
}
