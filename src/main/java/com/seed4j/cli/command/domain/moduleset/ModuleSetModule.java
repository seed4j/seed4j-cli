package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;
import java.util.Optional;

public record ModuleSetModule(
  ModuleSetSlug slug,
  List<ModuleSetDependency> dependencies,
  List<ModuleSetPropertyDefinition> properties,
  Optional<String> feature
) {
  public ModuleSetModule {
    Assert.notNull("slug", slug);
    Assert.notNull("dependencies", dependencies);
    Assert.notNull("properties", properties);
    Assert.notNull("feature", feature);
    dependencies = List.copyOf(dependencies);
    properties = List.copyOf(properties);
  }
}
