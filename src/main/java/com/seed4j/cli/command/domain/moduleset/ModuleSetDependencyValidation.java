package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;
import java.util.Optional;

public record ModuleSetDependencyValidation(
  ModuleSetDependency dependency,
  ModuleSetDependencyStatus status,
  Optional<ModuleSetSlug> provider,
  List<ModuleSetSlug> candidates,
  List<ModuleSetSlug> requiredBy
) {
  public ModuleSetDependencyValidation {
    Assert.notNull("dependency", dependency);
    Assert.notNull("status", status);
    Assert.notNull("provider", provider);
    Assert.notNull("candidates", candidates);
    Assert.notNull("requiredBy", requiredBy);
    candidates = List.copyOf(candidates);
    requiredBy = List.copyOf(requiredBy);
  }
}
