package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

public record ModuleSetPlan(
  RequestedModuleSet requestedModules,
  ModuleSetProjectPath projectPath,
  List<ModuleSetPlanItem> items,
  ModuleSetCommitMode commitMode,
  ModuleSetDetailedPlanningStatus detailedPlanningStatus,
  EffectiveModuleSetParameters effectiveParameters,
  List<ModuleSetDependencyValidation> dependencyValidations,
  List<ResolvedModuleSetParameter> resolvedParameters,
  List<MissingRequiredModuleSetParameter> missingRequiredParameters,
  List<ModuleSetPlanningProblem> problems,
  List<ModuleSetPlanningWarning> warnings
) {
  public ModuleSetPlan {
    Assert.notNull("requestedModules", requestedModules);
    Assert.notNull("projectPath", projectPath);
    Assert.notNull("items", items);
    Assert.notNull("commitMode", commitMode);
    Assert.notNull("detailedPlanningStatus", detailedPlanningStatus);
    Assert.notNull("effectiveParameters", effectiveParameters);
    Assert.notNull("dependencyValidations", dependencyValidations);
    Assert.notNull("resolvedParameters", resolvedParameters);
    Assert.notNull("missingRequiredParameters", missingRequiredParameters);
    Assert.notNull("problems", problems);
    Assert.notNull("warnings", warnings);
    items = List.copyOf(items);
    dependencyValidations = List.copyOf(dependencyValidations);
    resolvedParameters = List.copyOf(resolvedParameters);
    missingRequiredParameters = List.copyOf(missingRequiredParameters);
    problems = List.copyOf(problems);
    warnings = List.copyOf(warnings);
  }

  public List<ModuleSetSlug> executionOrder() {
    return items.stream().map(ModuleSetPlanItem::slug).toList();
  }

  public List<ResolvedModuleSetParameter> effectiveResolvedParameters() {
    return resolvedParameters.stream().filter(effectiveParameters::includes).toList();
  }

  public ModuleSetPlan withWarnings(List<ModuleSetPlanningWarning> planWarnings) {
    return new ModuleSetPlan(
      requestedModules,
      projectPath,
      items,
      commitMode,
      detailedPlanningStatus,
      effectiveParameters,
      dependencyValidations,
      resolvedParameters,
      missingRequiredParameters,
      problems,
      planWarnings
    );
  }

  public boolean valid() {
    return (
      problems.isEmpty()
      && missingRequiredParameters.isEmpty()
      && dependencyValidations.stream().noneMatch(validation -> validation.status() == ModuleSetDependencyStatus.MISSING)
    );
  }
}
