package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record BashCompletionInstallationResult(BashCompletionInstallationPath path) {
  public BashCompletionInstallationResult {
    Assert.notNull("path", path);
  }
}
