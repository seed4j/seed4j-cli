package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record ModuleSetPlanningHistory(
  Set<ModuleSetSlug> appliedModules,
  Map<ModuleSetPropertyKey, ModuleSetParameterValue> parameters,
  List<UnsupportedModuleSetHistoryParameter> unsupportedParameters
) {
  public ModuleSetPlanningHistory {
    Assert.notNull("appliedModules", appliedModules);
    Assert.notNull("parameters", parameters);
    Assert.notNull("unsupportedParameters", unsupportedParameters);
    appliedModules = Set.copyOf(appliedModules);
    parameters = Map.copyOf(parameters);
    unsupportedParameters = List.copyOf(unsupportedParameters);
  }

  public Optional<UnsupportedModuleSetHistoryParameter> unsupportedParameter(ModuleSetPropertyKey key) {
    return unsupportedParameters
      .stream()
      .filter(parameter -> parameter.key().equals(key))
      .findFirst();
  }
}
