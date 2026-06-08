package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record RuntimeExtensionInstallResult(Path extensionJarPath, Path metadataPath, Path configPath, boolean runtimeReplaced) {
  public RuntimeExtensionInstallResult {
    Assert.notNull("extensionJarPath", extensionJarPath);
    Assert.notNull("metadataPath", metadataPath);
    Assert.notNull("configPath", configPath);
  }
}
