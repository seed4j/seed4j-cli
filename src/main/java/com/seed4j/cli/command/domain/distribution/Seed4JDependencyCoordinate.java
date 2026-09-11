package com.seed4j.cli.command.domain.distribution;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Optional;

public record Seed4JDependencyCoordinate(DependencyGroupId groupId, DependencyArtifactId artifactId, Optional<DependencyVersion> version) {
  public Seed4JDependencyCoordinate {
    Assert.notNull("groupId", groupId);
    Assert.notNull("artifactId", artifactId);
    Assert.notNull("version", version);
  }

  public static Seed4JDependencyCoordinate versioned(String groupId, String artifactId, String version) {
    return new Seed4JDependencyCoordinate(
      new DependencyGroupId(groupId),
      new DependencyArtifactId(artifactId),
      Optional.of(new DependencyVersion(version))
    );
  }

  public static Seed4JDependencyCoordinate unversioned(String groupId, String artifactId) {
    return new Seed4JDependencyCoordinate(new DependencyGroupId(groupId), new DependencyArtifactId(artifactId), Optional.empty());
  }
}
