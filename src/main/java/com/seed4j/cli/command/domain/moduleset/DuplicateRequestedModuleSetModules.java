package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record DuplicateRequestedModuleSetModules(List<ModuleSetSlug> modules) implements ModuleSetPlanningProblem {
  public DuplicateRequestedModuleSetModules {
    Assert.notNull("modules", modules);
    modules = List.copyOf(modules);
  }
}
