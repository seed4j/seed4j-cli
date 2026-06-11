package com.seed4j.cli.bootstrap.infrastructure.secondary;

@FunctionalInterface
interface SpringApplicationBuilderOperationsFactory {
  SpringApplicationBuilderOperations create();
}
