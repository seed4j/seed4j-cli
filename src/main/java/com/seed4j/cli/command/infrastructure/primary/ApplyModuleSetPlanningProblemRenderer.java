package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.moduleset.DuplicateRequestedModuleSetModules;
import com.seed4j.cli.command.domain.moduleset.InvalidModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionOrderMismatch;
import com.seed4j.cli.command.domain.moduleset.ModuleSetHistoryParameterTypeMismatch;
import com.seed4j.cli.command.domain.moduleset.ModuleSetHistoryParameterValueType;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningProblem;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyConflict;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyConflicts;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefaultConflict;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefaultValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDescriptionConflict;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyTypeConflict;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.UnknownRequestedModuleSetModules;
import com.seed4j.cli.command.domain.moduleset.UnusedExplicitModuleSetParameters;
import java.util.List;
import java.util.stream.Collectors;

final class ApplyModuleSetPlanningProblemRenderer {

  String problems(List<ModuleSetPlanningProblem> problems) {
    if (problems.isEmpty()) {
      return "";
    }
    StringBuilder output = new StringBuilder();
    output.append("Validation problems:\n");
    problems.forEach(problem -> output.append("  ○ ").append(problemText(problem)).append('\n'));
    return output.append('\n').toString();
  }

  private static String problemText(ModuleSetPlanningProblem problem) {
    return switch (problem) {
      case DuplicateRequestedModuleSetModules duplicateModules -> duplicateModules(duplicateModules);
      case InvalidModuleSetProjectPath invalidPath -> invalidProjectPath(invalidPath);
      case ModuleSetExecutionOrderMismatch mismatch -> executionOrderMismatch(mismatch);
      case UnknownRequestedModuleSetModules unknownModules -> unknownModules(unknownModules);
      case ModuleSetPropertyConflicts propertyConflicts -> propertyConflicts(propertyConflicts);
      case ModuleSetHistoryParameterTypeMismatch mismatch -> historyMismatch(mismatch);
      case UnusedExplicitModuleSetParameters unusedParameters -> unusedParameters(unusedParameters);
    };
  }

  private static String duplicateModules(DuplicateRequestedModuleSetModules duplicateModules) {
    return moduleValues("Duplicate requested modules", duplicateModules.modules());
  }

  private static String unknownModules(UnknownRequestedModuleSetModules unknownModules) {
    return moduleValues("Unknown requested modules", unknownModules.modules());
  }

  private static String invalidProjectPath(InvalidModuleSetProjectPath invalidPath) {
    return switch (invalidPath) {
      case NOT_DIRECTORY -> "Project path exists but is not a directory";
      case NOT_ACCESSIBLE -> "Project path is not traversable and writable";
      case NOT_APPARENTLY_CREATABLE -> "Project path does not have a traversable, writable directory ancestor";
    };
  }

  private static String executionOrderMismatch(ModuleSetExecutionOrderMismatch mismatch) {
    return "Calculated execution order does not contain exactly the requested modules: requested %s; calculated %s".formatted(
      mismatch.requestedModules().stream().map(ModuleSetSlug::value).collect(Collectors.joining(", ")),
      mismatch.executionOrder().stream().map(ModuleSetSlug::value).collect(Collectors.joining(", "))
    );
  }

  private static String moduleValues(String label, List<ModuleSetSlug> modules) {
    return problemValues(label, modules.stream().map(ModuleSetSlug::value).toList());
  }

  private static String problemValues(String label, List<String> values) {
    return label + ": " + String.join(", ", values);
  }

  private static String propertyConflicts(ModuleSetPropertyConflicts propertyConflicts) {
    return problemValues(
      "Property conflicts",
      propertyConflicts.conflicts().stream().map(ApplyModuleSetPlanningProblemRenderer::propertyConflict).toList()
    );
  }

  private static String propertyConflict(ModuleSetPropertyConflict conflict) {
    return switch (conflict) {
      case ModuleSetPropertyDefaultConflict defaultConflict -> defaultConflict(defaultConflict);
      case ModuleSetPropertyDescriptionConflict descriptionConflict -> descriptionConflict(descriptionConflict);
      case ModuleSetPropertyTypeConflict typeConflict -> typeConflict(typeConflict);
    };
  }

  private static String defaultConflict(ModuleSetPropertyDefaultConflict conflict) {
    return "%s: conflicting defaults (%s)".formatted(
      conflict.key().value(),
      conflict.defaults().stream().map(ModuleSetPropertyDefaultValue::literal).collect(Collectors.joining(", "))
    );
  }

  private static String descriptionConflict(ModuleSetPropertyDescriptionConflict conflict) {
    return "%s: conflicting descriptions (%s)".formatted(
      conflict.key().value(),
      conflict
        .descriptions()
        .stream()
        .map(description -> description.value())
        .collect(Collectors.joining(", "))
    );
  }

  private static String typeConflict(ModuleSetPropertyTypeConflict conflict) {
    return "%s: conflicting types (%s)".formatted(
      conflict.key().value(),
      conflict.types().stream().map(Enum::name).collect(Collectors.joining(", "))
    );
  }

  private static String historyMismatch(ModuleSetHistoryParameterTypeMismatch mismatch) {
    return "Project history parameter type mismatch: %s expects %s but history contains %s; pass %s to override the stored value".formatted(
      mismatch.key().value(),
      mismatch.expectedType(),
      historyType(mismatch.historyType()),
      ModulePropertyOptionSpecFactory.toDashedFormat(mismatch.key().value())
    );
  }

  private static String historyType(ModuleSetHistoryParameterValueType type) {
    return switch (type) {
      case STRING, INTEGER, BOOLEAN -> type.name();
      case UNSUPPORTED -> "an unsupported value type";
    };
  }

  private static String unusedParameters(UnusedExplicitModuleSetParameters unusedParameters) {
    return problemValues(
      "Options not used by requested modules",
      unusedParameters
        .keys()
        .stream()
        .map(key -> ModulePropertyOptionSpecFactory.toDashedFormat(key.value()))
        .toList()
    );
  }
}
