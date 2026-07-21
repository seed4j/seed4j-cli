package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Map;
import java.util.Set;

public record ModuleSetPlanningHistory(Set<ModuleSetSlug> appliedModules, Map<ModuleSetPropertyKey, Object> parameters) {
  public ModuleSetPlanningHistory {
    Assert.notNull("appliedModules", appliedModules);
    Assert.notNull("parameters", parameters);
    appliedModules = Set.copyOf(appliedModules);
    parameters = Map.copyOf(parameters);
  }
}
