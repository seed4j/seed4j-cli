package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record RuntimeExtensionMetadata(RuntimeDistributionId distributionId, RuntimeDistributionVersion distributionVersion) {
  public RuntimeExtensionMetadata {
    Assert.notNull("distributionId", distributionId);
    Assert.notNull("distributionVersion", distributionVersion);
  }
}
