package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record ChildRuntimeLaunchRequest(
  Path executableJar,
  RuntimeSelection runtimeSelection,
  Seed4JCliArguments arguments,
  boolean debug
) {
  public ChildRuntimeLaunchRequest {
    Assert.notNull("executableJar", executableJar);
    Assert.notNull("runtimeSelection", runtimeSelection);
    Assert.notNull("arguments", arguments);
  }
}
