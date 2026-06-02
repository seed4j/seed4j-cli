package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncher;
import com.seed4j.cli.shared.error.domain.Assert;

public class PreSpringBootstrapApplicationService {

  private final Seed4JCliLauncher seed4jCliLauncher;

  public PreSpringBootstrapApplicationService(Seed4JCliLauncher seed4jCliLauncher) {
    Assert.notNull("seed4jCliLauncher", seed4jCliLauncher);
    this.seed4jCliLauncher = seed4jCliLauncher;
  }

  public int exitCodeFor(Seed4JCliArguments arguments) {
    Assert.notNull("arguments", arguments);
    return seed4jCliLauncher.launch(arguments);
  }
}
