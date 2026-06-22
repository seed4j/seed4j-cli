package com.seed4j.cli.command.infrastructure.primary;

class ApplyModulePlanRenderer {

  private static final String PROJECT_HISTORY_NOTE = "already selected by project history; omit this option to keep it.";

  String render(String moduleSlug, String projectPath, ResolvedModuleParameters parameters) {
    StringBuilder plan = new StringBuilder();
    plan.append("Plan for module: ").append(moduleSlug).append('\n');
    plan.append("Project path: ").append(projectPath).append('\n');
    plan.append('\n');
    plan.append("Resolved parameters:").append('\n');

    if (!parameters.empty()) {
      for (ResolvedModuleParameter parameter : parameters.parameters()) {
        plan.append('\n');
        plan.append(parameter.name()).append(": ").append(parameter.value()).append('\n');
        plan.append("  Source: ").append(parameter.source().displayLabel()).append('\n');
        plan.append("  CLI option: ").append(parameter.cliOption()).append('\n');
        if (parameter.source().projectHistory()) {
          plan.append("  Note: ").append(PROJECT_HISTORY_NOTE).append('\n');
        }
      }
    }

    plan.append('\n');
    plan.append("No changes were applied.").append('\n');

    return plan.toString();
  }
}
