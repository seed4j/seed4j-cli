package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record PreSpringBootstrapCommand(
  Path userHomePath,
  Path executablePath,
  String currentSeed4JVersion,
  boolean childMode,
  String[] args
) {
  public PreSpringBootstrapCommand {
    Assert.notNull("userHomePath", userHomePath);
    Assert.notNull("executablePath", executablePath);
    Assert.notNull("currentSeed4JVersion", currentSeed4JVersion);
    Assert.notNull("args", args);
    args = args.clone();
  }

  @Override
  public String[] args() {
    return args.clone();
  }
}
