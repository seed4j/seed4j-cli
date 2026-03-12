package com.seed4j.cli.bootstrap;

import java.nio.file.Path;

public record RuntimeExtensionConfiguration(Path jarPath, Path metadataPath) {
  public static RuntimeExtensionConfiguration withDefaultPaths(Path userHome) {
    return new RuntimeExtensionConfiguration(
      userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar"),
      userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml")
    );
  }
}
