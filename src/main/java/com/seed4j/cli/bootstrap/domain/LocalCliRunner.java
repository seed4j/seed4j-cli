package com.seed4j.cli.bootstrap.domain;

@FunctionalInterface
public interface LocalCliRunner {
  int run(String[] args);
}
