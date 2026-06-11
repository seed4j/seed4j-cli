package com.seed4j.cli.bootstrap.infrastructure.secondary;

interface SpringApplicationBuilderOperations {
  SpringApplicationBuilderOperations bannerModeOff();

  SpringApplicationBuilderOperations webNone();

  SpringApplicationBuilderOperations lazyInitialization(boolean lazyInitialization);

  SpringApplicationBuilderOperations properties(String properties);

  SpringApplicationContextAdapter run(String[] args);
}
