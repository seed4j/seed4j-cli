package com.seed4j.cli.bootstrap.infrastructure.primary;

@FunctionalInterface
public interface PreSpringBootstrapEntryPoint {
  int launch(String[] args);
}
