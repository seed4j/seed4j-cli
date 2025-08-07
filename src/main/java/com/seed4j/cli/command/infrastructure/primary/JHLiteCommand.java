package com.seed4j.cli.command.infrastructure.primary;

import picocli.CommandLine.Model.CommandSpec;

interface JHLiteCommand {
  CommandSpec spec();

  String name();
}
