package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;
import java.util.Optional;

public record ModuleSetPropertyDefinition(
  ModuleSetPropertyKey key,
  ModuleSetPropertyType type,
  ModuleSetPropertyRequirement requirement,
  Optional<ModuleSetPropertyDescription> description,
  Optional<ModuleSetPropertyDefaultValue> defaultValue,
  List<String> completionCandidates
) {
  public ModuleSetPropertyDefinition {
    Assert.notNull("key", key);
    Assert.notNull("type", type);
    Assert.notNull("requirement", requirement);
    Assert.notNull("description", description);
    Assert.notNull("defaultValue", defaultValue);
    Assert.notNull("completionCandidates", completionCandidates);
    completionCandidates = List.copyOf(completionCandidates);
  }

  public boolean mandatory() {
    return requirement.mandatory();
  }
}
