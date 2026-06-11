package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeExtensionInstallResult(
  RuntimeExtensionInstalledJarPath extensionJarPath,
  RuntimeExtensionMetadataPath metadataPath,
  RuntimeModeConfigurationPath configPath,
  RuntimeExtensionReplacementStatus replacementStatus
) {
  public RuntimeExtensionInstallResult {
    Assert.notNull("extensionJarPath", extensionJarPath);
    Assert.notNull("metadataPath", metadataPath);
    Assert.notNull("configPath", configPath);
    Assert.notNull("replacementStatus", replacementStatus);
  }
}
