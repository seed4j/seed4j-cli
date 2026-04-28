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
  private static final RuntimeExtensionJarLayoutValidator RUNTIME_EXTENSION_JAR_LAYOUT_VALIDATOR = new RuntimeExtensionJarLayoutValidator();

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

    RUNTIME_EXTENSION_JAR_LAYOUT_VALIDATOR.validate(runtimeConfiguration.extension().jarPath());

    RuntimeMetadata metadata = RuntimeMetadata.read(runtimeConfiguration.extension().metadataPath());
    metadata
      .compatibilityMinCliVersion()
      .ifPresent(version -> CliVersion.current(currentCliVersion).validateCompatibilityWith(CliVersion.minimumCompatibility(version)));

    return new RuntimeSelection(
      RuntimeMode.EXTENSION,
      Optional.of(runtimeConfiguration.extension().jarPath()),
      Optional.of(metadata.distributionId()),
      Optional.of(metadata.distributionVersion())
    );
  }
}
