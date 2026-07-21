package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetDependencyValidation;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningProblem;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningProblemType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertySource;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.ResolvedModuleSetParameter;
import java.util.List;
import java.util.Map;

class ApplyModuleSetPlanRenderer {

  private static final Map<ModuleSetPlanningProblemType, String> PROBLEM_LABELS = Map.of(
    ModuleSetPlanningProblemType.DUPLICATE_MODULES,
    "Duplicate requested modules",
    ModuleSetPlanningProblemType.UNKNOWN_MODULES,
    "Unknown requested modules",
    ModuleSetPlanningProblemType.PROPERTY_CONFLICT,
    "Property conflicts",
    ModuleSetPlanningProblemType.IRRELEVANT_OPTION,
    "Options not used by requested modules"
  );

  String render(ModuleSetPlan plan) {
    StringBuilder output = new StringBuilder("Plan for module set\n\n");
    output.append("Project path: ").append(plan.projectPath().value()).append("\n\n");
    appendModules(output, "Requested modules", plan.requestedModules().modules());
    appendModules(output, "Execution order", plan.executionOrder());
    appendDependencies(output, plan.dependencyValidations());
    appendResolvedParameters(output, plan.resolvedParameters());
    appendMissingParameters(output, plan);
    appendProblems(output, plan.problems());
    output.append("Status: ").append(plan.valid() ? "VALID" : "INVALID").append('\n');
    output.append("No changes were applied.\n");
    return output.toString();
  }

  private static void appendModules(StringBuilder output, String heading, List<ModuleSetSlug> modules) {
    output.append(heading).append(":\n");
    for (int index = 0; index < modules.size(); index++) {
      output.append("  ").append(index + 1).append(". ").append(modules.get(index).value()).append('\n');
    }
    output.append('\n');
  }

  private static void appendDependencies(StringBuilder output, List<ModuleSetDependencyValidation> dependencies) {
    output.append("Dependency validation:\n");
    if (dependencies.isEmpty()) {
      output.append("  ✓ No dependencies.\n");
    }
    for (ModuleSetDependencyValidation validation : dependencies) {
      output
        .append("  ")
        .append(validation.status() == ModuleSetDependencyStatus.MISSING ? "○ " : "✓ ")
        .append(validation.dependency().token())
        .append(" - ")
        .append(dependencyResolution(validation))
        .append("; required by: ")
        .append(validation.requiredBy().stream().map(ModuleSetSlug::value).collect(java.util.stream.Collectors.joining(", ")))
        .append('\n');
    }
    output.append('\n');
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

  private static void appendResolvedParameters(StringBuilder output, List<ResolvedModuleSetParameter> parameters) {
    output.append("Resolved parameters:\n");
    if (parameters.isEmpty()) {
      output.append("  (none)\n");
    }
    for (ResolvedModuleSetParameter parameter : parameters) {
      output
        .append("  ✓ ")
        .append(parameter.key().value())
        .append(": ")
        .append(parameter.value())
        .append("\n    Source: ")
        .append(source(parameter.source()))
        .append("\n    CLI option: ")
        .append(ModulePropertyOptionSpecFactory.toDashedFormat(parameter.key().value()))
        .append('\n');
    }
    output.append('\n');
  }

  private static String source(ModuleSetPropertySource source) {
    return switch (source) {
      case EXPLICIT_CLI -> "explicit CLI input";
      case PROJECT_HISTORY -> "project history";
      case DEFAULT -> "default (informational)";
    };
  }

  private static void appendMissingParameters(StringBuilder output, ModuleSetPlan plan) {
    if (plan.missingRequiredParameters().isEmpty()) {
      return;
    }
    output.append("Missing required parameters:\n");
    plan
      .missingRequiredParameters()
      .forEach(parameter ->
        output
          .append("  ○ ")
          .append(parameter.key().value())
          .append(" (")
          .append(ModulePropertyOptionSpecFactory.toDashedFormat(parameter.key().value()))
          .append(")\n")
      );
    output.append('\n');
  }

  private static void appendProblems(StringBuilder output, List<ModuleSetPlanningProblem> problems) {
    List<ModuleSetPlanningProblem> otherProblems = problems
      .stream()
      .filter(problem -> PROBLEM_LABELS.containsKey(problem.type()))
      .toList();
    if (otherProblems.isEmpty()) {
      return;
    }
    output.append("Validation problems:\n");
    otherProblems.forEach(problem ->
      output.append("  ○ ").append(PROBLEM_LABELS.get(problem.type())).append(": ").append(String.join(", ", problem.values())).append('\n')
    );
    output.append('\n');
  }
}
