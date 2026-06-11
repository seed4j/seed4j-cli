package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeExtensionInstallResult(
  RuntimeExtensionJarPath extensionJarPath,
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
