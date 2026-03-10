package com.seed4j.cli.bootstrap;

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

    return new RuntimeSelection(RuntimeMode.EXTENSION, Optional.of(runtimeConfiguration.extension().jarPath()));
  }
}
