package com.seed4j.cli.command.infrastructure.primary;

import picocli.CommandLine.Model.CommandSpec;

interface Seed4JCommand {
  CommandSpec spec();

  String name();
}
