package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.ModuleSetExecutionApplicationService;
import com.seed4j.cli.command.application.ModuleSetPlanningApplicationService;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class ApplyModuleSetCommand implements Seed4JCommand {

  private final ModuleSetPlanningApplicationService planning;
  private final ModuleSetExecutionApplicationService execution;

  public ApplyModuleSetCommand(ModuleSetPlanningApplicationService planning, ModuleSetExecutionApplicationService execution) {
    Assert.notNull("planning", planning);
    Assert.notNull("execution", execution);
    this.planning = planning;
    this.execution = execution;
  }

  @Override
  public CommandSpec spec() {
    return new ApplyModuleSetInvocation(planning, execution, name()).spec();
  }

  @Override
  public String name() {
    return "apply-set";
  }
}
