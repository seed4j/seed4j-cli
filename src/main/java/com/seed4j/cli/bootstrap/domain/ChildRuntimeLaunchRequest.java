package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record ChildRuntimeLaunchRequest(Seed4JCliExecutablePath executableJar, Seed4JCliArguments arguments) {
  public ChildRuntimeLaunchRequest {
    Assert.notNull("executableJar", executableJar);
    Assert.notNull("arguments", arguments);
  }
}
