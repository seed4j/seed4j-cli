package com.seed4j.cli.bootstrap;

@FunctionalInterface
interface LocalCliRunner {
  int run(String[] args);
}
