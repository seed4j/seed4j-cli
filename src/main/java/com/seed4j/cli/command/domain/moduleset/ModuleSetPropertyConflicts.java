package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record ModuleSetPropertyConflicts(List<ModuleSetPropertyConflict> conflicts) implements ModuleSetPlanningProblem {
  public ModuleSetPropertyConflicts {
    Assert.notNull("conflicts", conflicts);
    conflicts = List.copyOf(conflicts);
  }
}
