package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record UnusedExplicitModuleSetParameters(List<ModuleSetPropertyKey> keys) implements ModuleSetPlanningProblem {
  public UnusedExplicitModuleSetParameters {
    Assert.notNull("keys", keys);
    keys = List.copyOf(keys);
  }
}
