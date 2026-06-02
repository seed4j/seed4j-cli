package com.seed4j.cli.bootstrap.domain;

@FunctionalInterface
public interface LocalCliRunner {
  int run(Seed4JCliArguments arguments);
}
