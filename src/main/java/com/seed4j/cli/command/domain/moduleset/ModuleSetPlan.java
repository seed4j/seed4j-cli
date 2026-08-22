package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record ModuleSetPlan(
  RequestedModuleSet requestedModules,
  ModuleSetProjectPath projectPath,
  List<ModuleSetSlug> executionOrder,
  List<ModuleSetDependencyValidation> dependencyValidations,
  List<ResolvedModuleSetParameter> resolvedParameters,
  List<MissingRequiredModuleSetParameter> missingRequiredParameters,
  List<ModuleSetPlanningProblem> problems
) {
  public ModuleSetPlan {
    Assert.notNull("requestedModules", requestedModules);
    Assert.notNull("projectPath", projectPath);
    Assert.notNull("executionOrder", executionOrder);
    Assert.notNull("dependencyValidations", dependencyValidations);
    Assert.notNull("resolvedParameters", resolvedParameters);
    Assert.notNull("missingRequiredParameters", missingRequiredParameters);
    Assert.notNull("problems", problems);
    executionOrder = List.copyOf(executionOrder);
    dependencyValidations = List.copyOf(dependencyValidations);
    resolvedParameters = List.copyOf(resolvedParameters);
    missingRequiredParameters = List.copyOf(missingRequiredParameters);
    problems = List.copyOf(problems);
  }

  public boolean valid() {
    return (
      problems.isEmpty()
      && missingRequiredParameters.isEmpty()
      && dependencyValidations.stream().noneMatch(validation -> validation.status() == ModuleSetDependencyStatus.MISSING)
    );
  }
}
