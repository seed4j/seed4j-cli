package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record ChildRuntimeLaunchRequest(
  Seed4JCliExecutablePath executableJar,
  RuntimeSelection runtimeSelection,
  Seed4JCliArguments arguments,
  BootstrapDebugMode debug
) {
  public ChildRuntimeLaunchRequest {
    Assert.notNull("executableJar", executableJar);
    Assert.notNull("runtimeSelection", runtimeSelection);
    Assert.notNull("arguments", arguments);
    Assert.notNull("debug", debug);
  }
}
