package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;

class ApplyModuleSetExecutionPreflightRenderer {

  String render(ModuleSetPlan plan) {
    StringBuilder output = new StringBuilder("Preflight: VALID\n");
    output.append(ApplyModuleSetPreflightSectionsRenderer.executionOrder(plan.items()));
    output.append(ApplyModuleSetPreflightSectionsRenderer.parameters("Effective parameters", plan.effectiveResolvedParameters()));
    output.append(ApplyModuleSetPreflightSectionsRenderer.commitMode(plan));
    return output.toString();
  }
}
