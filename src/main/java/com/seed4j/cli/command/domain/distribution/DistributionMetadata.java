package com.seed4j.cli.command.domain.distribution;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Optional;
import java.util.Set;

public record DistributionMetadata(DistributionIdentity identity, Seed4JModuleAvailability moduleAvailability) {
  private static final DistributionModuleSlug EXTENSION_GENERATOR = new DistributionModuleSlug("seed4j-extension");
  private static final Seed4JDependencyCoordinate STABLE_COORDINATE = Seed4JDependencyCoordinate.unversioned("com.seed4j", "seed4j");

  public DistributionMetadata {
    Assert.notNull("identity", identity);
    Assert.notNull("moduleAvailability", moduleAvailability);
    if (identity.channel().experimental() && !moduleAvailability.unavailableModules().equals(Set.of(EXTENSION_GENERATOR))) {
      throw new IllegalArgumentException("Experimental distribution must make only seed4j-extension unavailable");
    }
    if (!identity.channel().experimental() && !moduleAvailability.unavailableModules().isEmpty()) {
      throw new IllegalArgumentException("Stable distribution must make every Seed4J module available");
    }
  }

  public static DistributionMetadata stable() {
    return new DistributionMetadata(
      new DistributionIdentity(ReleaseChannel.STABLE, STABLE_COORDINATE, Optional.empty()),
      Seed4JModuleAvailability.allAvailable()
    );
  }
}
