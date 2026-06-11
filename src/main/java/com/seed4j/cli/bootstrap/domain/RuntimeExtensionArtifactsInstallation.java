package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeExtensionArtifactsInstallation(RuntimeExtensionJarPath extensionJarPath, RuntimeExtensionMetadataPath metadataPath) {
  public RuntimeExtensionArtifactsInstallation {
    Assert.notNull("extensionJarPath", extensionJarPath);
    Assert.notNull("metadataPath", metadataPath);
  }
}
