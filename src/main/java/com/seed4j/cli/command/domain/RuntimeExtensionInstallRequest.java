package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeExtensionInstallRequest(
  RuntimeExtensionJarPath extensionJarPath,
  RuntimeDistributionId distributionId,
  RuntimeDistributionVersion distributionVersion
) {
  public RuntimeExtensionInstallRequest {
    Assert.notNull("extensionJarPath", extensionJarPath);
    Assert.notNull("distributionId", distributionId);
    Assert.notNull("distributionVersion", distributionVersion);
  }
}
