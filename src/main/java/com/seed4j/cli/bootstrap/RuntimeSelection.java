package com.seed4j.cli.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.Yaml;

public record RuntimeSelection(RuntimeMode mode, Optional<Path> extensionJarPath) {
  private static final String EXPECTED_DISTRIBUTION_KIND = "extension";

  public static RuntimeSelection resolve(RuntimeConfiguration runtimeConfiguration) {
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

    String distributionKind = distributionKind(runtimeConfiguration.extension().metadataPath());
    if (!EXPECTED_DISTRIBUTION_KIND.equals(distributionKind)) {
      throw new InvalidRuntimeConfigurationException("Invalid distribution.kind, expected extension but got: " + distributionKind);
    }

    return new RuntimeSelection(RuntimeMode.EXTENSION, Optional.of(runtimeConfiguration.extension().jarPath()));
  }

  @SuppressWarnings("unchecked")
  private static String distributionKind(Path metadataPath) {
    try {
      Map<String, Object> metadata = new Yaml().load(Files.newInputStream(metadataPath));
      if (metadata == null) {
        throw new InvalidRuntimeConfigurationException("Invalid runtime metadata file: " + metadataPath);
      }

      Map<String, Object> distribution = (Map<String, Object>) metadata.get("distribution");
      if (distribution == null || distribution.get("kind") == null) {
        throw new InvalidRuntimeConfigurationException("Invalid runtime metadata file: " + metadataPath);
      }

      return String.valueOf(distribution.get("kind"));
    } catch (IOException e) {
      throw new InvalidRuntimeConfigurationException("Invalid runtime metadata file: " + metadataPath);
    }
  }
}
