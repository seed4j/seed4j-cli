package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeExtensionInstallRequest(String extensionJarPath, String distributionId, String distributionVersion) {
  public RuntimeExtensionInstallRequest {
    Assert.notBlank("extensionJarPath", extensionJarPath);
    Assert.notBlank("distributionId", distributionId);
    Assert.notBlank("distributionVersion", distributionVersion);
  }
}
