package com.seed4j.cli.command.infrastructure.primary;

class ApplyModulePlanRenderer {

  private static final String PROJECT_HISTORY_NOTE = "already selected by project history; omit this option to keep it.";
  private static final String MISSING_REQUIRED_NOTE = "pass this option or apply a module that records it in project history.";

  String render(String moduleSlug, String projectPath, ApplyModuleDependencyPlan dependencyPlan, ResolvedModuleParameters parameters) {
    StringBuilder plan = new StringBuilder();
    renderHeader(plan, moduleSlug, projectPath);
    renderDependencyPlan(plan, dependencyPlan);
    renderResolvedParameters(plan, parameters);
    renderMissingRequiredParameters(plan, parameters);
    renderFooter(plan);

    return plan.toString();
  }

  private void renderHeader(StringBuilder plan, String moduleSlug, String projectPath) {
    plan.append("Plan for module: ").append(moduleSlug).append('\n');
    plan.append("Project path: ").append(projectPath).append('\n');
    plan.append('\n');
  }

  private void renderDependencyPlan(StringBuilder plan, ApplyModuleDependencyPlan dependencyPlan) {
    plan.append("Dependency plan:").append('\n');
    if (dependencyPlan.empty()) {
      plan.append('\n');
      plan.append(ApplyModulePlanItemMarker.RESOLVED.prefix()).append("No dependencies.").append('\n');
    } else {
      plan.append('\n');
      for (ApplyModuleDependencyPlanLine line : dependencyPlan.lines()) {
        renderDependency(plan, line);
      }
    }
  }

  private void renderDependency(StringBuilder plan, ApplyModuleDependencyPlanLine line) {
    plan
      .append(line.status().planMarker().prefix())
      .append(line.dependency())
      .append(" - ")
      .append(line.status().displayLabel())
      .append('\n');
  }

  private void renderResolvedParameters(StringBuilder plan, ResolvedModuleParameters parameters) {
    plan.append('\n');
    plan.append("Resolved parameters:").append('\n');

    if (!parameters.empty()) {
      for (ResolvedModuleParameter parameter : parameters.parameters()) {
        renderResolvedParameter(plan, parameter);
      }
    }
  }

  private void renderResolvedParameter(StringBuilder plan, ResolvedModuleParameter parameter) {
    plan.append('\n');
    plan.append(ApplyModulePlanItemMarker.RESOLVED.prefix()).append(parameter.name()).append(": ").append(parameter.value()).append('\n');
    plan.append("  Source: ").append(parameter.source().displayLabel()).append('\n');
    plan.append("  CLI option: ").append(parameter.cliOption()).append('\n');
    if (parameter.source().projectHistory()) {
      plan.append("  Note: ").append(PROJECT_HISTORY_NOTE).append('\n');
    }
  }

  private void renderMissingRequiredParameters(StringBuilder plan, ResolvedModuleParameters parameters) {
    if (!parameters.complete()) {
      plan.append('\n');
      plan.append("Missing required parameters:").append('\n');
      for (MissingRequiredModuleParameter parameter : parameters.missingRequiredParameters()) {
        renderMissingRequiredParameter(plan, parameter);
      }
    }
  }

  private void renderMissingRequiredParameter(StringBuilder plan, MissingRequiredModuleParameter parameter) {
    plan.append('\n');
    plan.append(ApplyModulePlanItemMarker.PENDING.prefix()).append(parameter.name()).append(":").append('\n');
    plan.append("  CLI option: ").append(parameter.cliOption()).append('\n');
    plan.append("  Note: ").append(MISSING_REQUIRED_NOTE).append('\n');
  }

  private void renderFooter(StringBuilder plan) {
    plan.append('\n');
    plan.append("No changes were applied.").append('\n');
  }
}
