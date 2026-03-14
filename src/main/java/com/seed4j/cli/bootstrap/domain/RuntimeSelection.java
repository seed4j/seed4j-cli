package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public record RuntimeSelection(
  RuntimeMode mode,
  Optional<Path> extensionJarPath,
  Optional<String> distributionId,
  Optional<String> distributionVersion
) {
  private static final String EXPECTED_DISTRIBUTION_KIND = "extension";

  public static RuntimeSelection resolve(RuntimeConfiguration runtimeConfiguration, String currentCliVersion) {
    if (runtimeConfiguration == null) {
      return new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());
    }

    if (runtimeConfiguration.mode() == RuntimeMode.STANDARD) {
      return new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());
    }

    if (!Files.exists(runtimeConfiguration.extension().metadataPath())) {
      throw new InvalidRuntimeConfigurationException("Invalid runtime metadata file: " + runtimeConfiguration.extension().metadataPath());
    }

    if (!Files.exists(runtimeConfiguration.extension().jarPath())) {
      throw new InvalidRuntimeConfigurationException("Invalid runtime jar file: " + runtimeConfiguration.extension().jarPath());
    }

    RuntimeMetadata metadata = RuntimeMetadata.read(runtimeConfiguration.extension().metadataPath());
    if (!EXPECTED_DISTRIBUTION_KIND.equals(metadata.distributionKind())) {
      throw new InvalidRuntimeConfigurationException(
        "Invalid distribution.kind, expected extension but got: " + metadata.distributionKind()
      );
    }

    String selectedJarFilename = runtimeConfiguration.extension().jarPath().getFileName().toString();
    if (!selectedJarFilename.equals(metadata.artifactFilename())) {
      throw new InvalidRuntimeConfigurationException(
        "Invalid artifact.filename, expected " + selectedJarFilename + " but got: " + metadata.artifactFilename()
      );
    }

    CliVersion.current(currentCliVersion).validateCompatibilityWith(CliVersion.minimumCompatibility(metadata.compatibilityCli()));

    return new RuntimeSelection(
      RuntimeMode.EXTENSION,
      Optional.of(runtimeConfiguration.extension().jarPath()),
      Optional.of(metadata.distributionId()),
      Optional.of(metadata.distributionVersion())
    );
  }
}
