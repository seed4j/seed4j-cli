package com.seed4j.cli.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public record RuntimeSelection(RuntimeMode mode, Optional<Path> extensionJarPath) {
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

    return new RuntimeSelection(RuntimeMode.EXTENSION, Optional.of(runtimeConfiguration.extension().jarPath()));
  }
}
