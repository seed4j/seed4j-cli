package com.seed4j.cli.bootstrap.application;

@FunctionalInterface
public interface PreSpringLauncher {
  int launch(String[] args, boolean childMode);
}
