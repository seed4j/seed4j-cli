package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record UnknownRequestedModuleSetModules(List<ModuleSetSlug> modules) implements ModuleSetPlanningProblem {
  public UnknownRequestedModuleSetModules {
    Assert.notNull("modules", modules);
    modules = List.copyOf(modules);
  }
}
