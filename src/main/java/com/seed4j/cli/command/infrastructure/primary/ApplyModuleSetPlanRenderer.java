package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyValidation;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import java.util.List;

class ApplyModuleSetPlanRenderer {

  private final ApplyModuleSetPlanningProblemRenderer problemRenderer = new ApplyModuleSetPlanningProblemRenderer();

  String render(ModuleSetPlan plan) {
    return heading(plan) + selection(plan) + validation(plan);
  }

  private static String heading(ModuleSetPlan plan) {
    StringBuilder output = new StringBuilder("Preflight: ").append(plan.valid() ? "VALID" : "INVALID").append('\n');
    output.append("Plan for module set\n\n");
    output.append("Project path: ").append(plan.projectPath().value()).append("\n\n");
    return output.toString();
  }

  private static String selection(ModuleSetPlan plan) {
    return (
      modules("Requested modules", plan.requestedModules().modules()) + ApplyModuleSetPreflightSectionsRenderer.executionOrder(plan.items())
    );
  }

  private String validation(ModuleSetPlan plan) {
    StringBuilder output = new StringBuilder(detailedPlanning(plan));
    output.append(ApplyModuleSetPreflightSectionsRenderer.commitMode(plan)).append('\n');
    output.append(missingParameters(plan));
    output.append(problemRenderer.problems(plan.problems()));
    return output.append(status(plan.valid())).toString();
  }

  private static String detailedPlanning(ModuleSetPlan plan) {
    if (plan.detailedPlanningStatus().notEvaluated()) {
      return "Dependency validation: (not evaluated)\n\nResolved parameters: (not evaluated)\n\n";
    }
    return (
      dependencies(plan.dependencyValidations())
      + ApplyModuleSetPreflightSectionsRenderer.parameters("Resolved parameters", plan.resolvedParameters())
    );
  }

  private static String modules(String heading, List<ModuleSetSlug> modules) {
    StringBuilder section = new StringBuilder(heading).append(":\n");
    for (int index = 0; index < modules.size(); index++) {
      section
        .append("  ")
        .append(index + 1)
        .append(". ")
        .append(modules.get(index).value())
        .append('\n');
    }
    return section.append('\n').toString();
  }

  private static String dependencies(List<ModuleSetDependencyValidation> dependencies) {
    StringBuilder section = new StringBuilder("Dependency validation:\n");
    if (dependencies.isEmpty()) {
      section.append("  ✓ No dependencies.\n");
    }
    for (ModuleSetDependencyValidation validation : dependencies) {
      section
        .append("  ")
        .append(validation.status() == ModuleSetDependencyStatus.MISSING ? "○ " : "✓ ")
        .append(validation.dependency().token())
        .append(" - ")
        .append(dependencyResolution(validation))
        .append("; required by: ")
        .append(validation.requiredBy().stream().map(ModuleSetSlug::value).collect(java.util.stream.Collectors.joining(", ")))
        .append('\n');
    }
    return section.append('\n').toString();
  }

  private static String dependencyResolution(ModuleSetDependencyValidation validation) {
    return switch (validation.status()) {
      case SATISFIED_BY_HISTORY -> "satisfied by project history: " + validation.provider().orElseThrow().value();
      case SATISFIED_BY_REQUESTED_MODULE -> "satisfied by requested module: " + validation.provider().orElseThrow().value();
      case MISSING -> missingDependencyResolution(validation);
    };
  }

  private static String missingDependencyResolution(ModuleSetDependencyValidation validation) {
    if (validation.dependency().type() == ModuleSetDependencyType.MODULE) {
      return "missing";
    }
    String candidates = validation.candidates().stream().map(ModuleSetSlug::value).collect(java.util.stream.Collectors.joining(", "));
    return "missing; select one explicitly from: " + candidates;
  }

  private static String missingParameters(ModuleSetPlan plan) {
    if (plan.missingRequiredParameters().isEmpty()) {
      return "";
    }
    StringBuilder section = new StringBuilder("Missing required parameters:\n");
    plan
      .missingRequiredParameters()
      .forEach(parameter ->
        section
          .append("  ○ ")
          .append(parameter.key().value())
          .append(" (")
          .append(ModulePropertyOptionSpecFactory.toDashedFormat(parameter.key().value()))
          .append(")\n")
      );
    return section.append('\n').toString();
  }

  private static String status(boolean valid) {
    return new StringBuilder()
      .append("Status: ")
      .append(valid ? "VALID" : "INVALID")
      .append('\n')
      .append("No changes were applied.\n")
      .toString();
  }
}
