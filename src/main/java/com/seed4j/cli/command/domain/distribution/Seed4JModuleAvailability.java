package com.seed4j.cli.command.domain.distribution;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Set;

public record Seed4JModuleAvailability(Set<DistributionModuleSlug> unavailableModules) {
  public Seed4JModuleAvailability {
    Assert.notNull("unavailableModules", unavailableModules);
    unavailableModules = Set.copyOf(unavailableModules);
  }

  public static Seed4JModuleAvailability allAvailable() {
    return new Seed4JModuleAvailability(Set.of());
  }

  public boolean available(String slug) {
    return !unavailableModules.contains(new DistributionModuleSlug(slug));
  }
}
