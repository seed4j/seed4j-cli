package com.seed4j.cli.bootstrap.domain;

@FunctionalInterface
interface LocalCliRunner {
  int run(String[] args);
}
