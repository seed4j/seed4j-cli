package com.seed4j.cli.bootstrap.domain;

import java.util.Optional;

public record RuntimeSelection(
  RuntimeMode mode,
  Optional<RuntimeExtensionJarPath> extensionJarPath,
  Optional<RuntimeDistributionId> distributionId,
  Optional<RuntimeDistributionVersion> distributionVersion
) {
  public static RuntimeSelection standard() {
    return new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());
  }

  public static RuntimeSelection extension(
    RuntimeExtensionJarPath extensionJarPath,
    RuntimeDistributionId distributionId,
    RuntimeDistributionVersion distributionVersion
  ) {
    return new RuntimeSelection(
      RuntimeMode.EXTENSION,
      Optional.of(extensionJarPath),
      Optional.of(distributionId),
      Optional.of(distributionVersion)
    );
  }

  public static RuntimeSelection extensionWithoutJar(
    Optional<RuntimeDistributionId> distributionId,
    Optional<RuntimeDistributionVersion> distributionVersion
  ) {
    return new RuntimeSelection(RuntimeMode.EXTENSION, Optional.empty(), distributionId, distributionVersion);
  }

  public boolean extensionMode() {
    return mode == RuntimeMode.EXTENSION;
  }
}
