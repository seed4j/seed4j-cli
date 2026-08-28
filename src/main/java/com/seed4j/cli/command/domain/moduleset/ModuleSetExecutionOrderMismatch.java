package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record ModuleSetExecutionOrderMismatch(
  List<ModuleSetSlug> requestedModules,
  List<ModuleSetSlug> executionOrder
) implements ModuleSetPlanningProblem {
  public ModuleSetExecutionOrderMismatch {
    Assert.notNull("requestedModules", requestedModules);
    Assert.notNull("executionOrder", executionOrder);
    requestedModules = List.copyOf(requestedModules);
    executionOrder = List.copyOf(executionOrder);
  }
}
