package com.seed4j.cli.command.infrastructure.primary;

class MissingApplyModuleDependenciesRenderer {

  String render(String moduleSlug, ApplyModuleDependencyPlan dependencyPlan) {
    StringBuilder diagnostic = new StringBuilder();
    diagnostic.append("Cannot apply module: ").append(moduleSlug).append('\n');
    diagnostic.append('\n');
    diagnostic.append("Missing required dependencies:").append('\n');
    diagnostic.append('\n');
    for (ApplyModuleDependencyPlanLine line : dependencyPlan.pendingLines()) {
      diagnostic
        .append(ApplyModulePlanItemMarker.PENDING.prefix())
        .append(line.dependency())
        .append(" - ")
        .append(line.status().displayLabel())
        .append('\n');
    }
    diagnostic.append('\n');
    diagnostic.append("Next action: apply every pending module and one module from each pending choice, then retry this module.");
    diagnostic.append('\n');
    diagnostic.append("No changes were applied.").append('\n');

    return diagnostic.toString();
  }
}
