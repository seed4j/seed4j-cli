package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;

class ApplyModuleSetExecutionPreflightRenderer {

  String render(ModuleSetPlan plan) {
    StringBuilder output = new StringBuilder("Preflight: VALID\n");
    ApplyModuleSetPreflightSectionsRenderer.appendExecutionOrder(output, plan.items());
    ApplyModuleSetPreflightSectionsRenderer.appendParameters(output, "Effective parameters", plan.effectiveResolvedParameters());
    ApplyModuleSetPreflightSectionsRenderer.appendCommitMode(output, plan);
    return output.toString();
  }
}
