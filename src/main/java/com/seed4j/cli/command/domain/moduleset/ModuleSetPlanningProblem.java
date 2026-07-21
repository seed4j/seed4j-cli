package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record ModuleSetPlanningProblem(ModuleSetPlanningProblemType type, List<String> values) {
  public ModuleSetPlanningProblem {
    Assert.notNull("type", type);
    Assert.notNull("values", values);
    values = List.copyOf(values);
  }
}
