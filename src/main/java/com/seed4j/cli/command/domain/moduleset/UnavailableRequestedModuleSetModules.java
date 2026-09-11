package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.command.domain.distribution.ReleaseChannel;
import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record UnavailableRequestedModuleSetModules(
  List<ModuleSetSlug> modules,
  ReleaseChannel channel
) implements ModuleSetPlanningProblem {
  public UnavailableRequestedModuleSetModules {
    Assert.notEmpty("modules", modules);
    Assert.notNull("channel", channel);
    modules = List.copyOf(modules);
  }
}
