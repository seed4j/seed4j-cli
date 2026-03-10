package com.seed4j.cli.bootstrap;

import java.nio.file.Path;

public record RuntimeExtensionConfiguration(Path jarPath, Path metadataPath) {
  public static RuntimeExtensionConfiguration withDefaultJarPath(Path metadataPath) {
    return new RuntimeExtensionConfiguration(
      Path.of(System.getProperty("user.home"), ".config", "seed4j-cli", "runtime", "active", "extension.jar"),
      metadataPath
    );
  }
}
