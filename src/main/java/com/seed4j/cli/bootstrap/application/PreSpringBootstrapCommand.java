package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.shared.error.domain.Assert;

public record PreSpringBootstrapCommand(String[] args) {
  public PreSpringBootstrapCommand {
    Assert.notNull("args", args);
    args = args.clone();
  }

  @Override
  public String[] args() {
    return args.clone();
  }
}
