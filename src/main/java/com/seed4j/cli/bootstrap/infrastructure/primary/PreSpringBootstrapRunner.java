package com.seed4j.cli.bootstrap.infrastructure.primary;

import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.shared.error.domain.Assert;

public class PreSpringBootstrapRunner {

  private final PreSpringBootstrapApplicationService preSpringBootstrapApplicationService;

  public PreSpringBootstrapRunner(PreSpringBootstrapApplicationService preSpringBootstrapApplicationService) {
    Assert.notNull("preSpringBootstrapApplicationService", preSpringBootstrapApplicationService);
    this.preSpringBootstrapApplicationService = preSpringBootstrapApplicationService;
  }

  public int exitCodeFor(String[] args) {
    Assert.notNull("args", args);
    return preSpringBootstrapApplicationService.exitCodeFor(new Seed4JCliArguments(args));
  }
}
