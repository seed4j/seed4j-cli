package com.seed4j.cli.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public record RuntimeSelection(RuntimeMode mode, Optional<Path> extensionJarPath) {
  private static final String EXPECTED_DISTRIBUTION_KIND = "extension";

  public static RuntimeSelection resolve(RuntimeConfiguration runtimeConfiguration) {
    return resolve(runtimeConfiguration, "");
  }

  public static RuntimeSelection resolve(RuntimeConfiguration runtimeConfiguration, String currentCliVersion) {
    if (runtimeConfiguration == null) {
      return new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty());
    }

    if (runtimeConfiguration.mode() == RuntimeMode.STANDARD) {
      return new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty());
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

    String normalizedCliVersion = normalizeCliVersion(currentCliVersion);
    if (!normalizedCliVersion.equals(metadata.compatibilityCli())) {
      throw new InvalidRuntimeConfigurationException(
        "Invalid compatibility.cli, expected "
          + metadata.compatibilityCli()
          + " to match current CLI version "
          + currentCliVersion
          + " (normalized as "
          + normalizedCliVersion
          + ")"
      );
    }

    return new RuntimeSelection(RuntimeMode.EXTENSION, Optional.of(runtimeConfiguration.extension().jarPath()));
  }

  private static String normalizeCliVersion(String currentCliVersion) {
    int qualifierIndex = currentCliVersion.indexOf('-');
    if (qualifierIndex < 0) {
      return currentCliVersion;
    }

    return currentCliVersion.substring(0, qualifierIndex);
  }
}
