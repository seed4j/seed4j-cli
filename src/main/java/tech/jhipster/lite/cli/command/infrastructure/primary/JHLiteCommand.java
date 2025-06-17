package tech.jhipster.lite.cli.command.infrastructure.primary;

import picocli.CommandLine.Model.CommandSpec;

interface JHLiteCommand {
  CommandSpec spec();

  String name();
}
