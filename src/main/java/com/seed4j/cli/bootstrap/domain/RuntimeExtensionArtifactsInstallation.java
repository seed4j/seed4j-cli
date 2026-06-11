package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record RuntimeExtensionArtifactsInstallation(Path extensionJarPath, Path metadataPath) {
  public RuntimeExtensionArtifactsInstallation {
    Assert.notNull("extensionJarPath", extensionJarPath);
    Assert.notNull("metadataPath", metadataPath);
  }
}
