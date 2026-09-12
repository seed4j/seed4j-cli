package com.seed4j.cli.command.domain.distribution;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Optional;

public record DistributionIdentity(
  ReleaseChannel channel,
  Seed4JDependencyCoordinate dependencyCoordinate,
  Optional<Seed4JUpstreamCommit> upstreamCommit
) {
  public DistributionIdentity {
    Assert.notNull("channel", channel);
    Assert.notNull("dependencyCoordinate", dependencyCoordinate);
    Assert.notNull("upstreamCommit", upstreamCommit);
    if (channel.experimental() && (upstreamCommit.isEmpty() || dependencyCoordinate.version().isEmpty())) {
      throw new IllegalArgumentException("Experimental distribution identity requires dependency version and upstream commit");
    }
  }
}
