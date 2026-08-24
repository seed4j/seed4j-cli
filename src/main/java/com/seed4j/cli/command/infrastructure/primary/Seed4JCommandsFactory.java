package com.seed4j.cli.command.infrastructure.primary;

import java.util.List;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

@Component
class Seed4JCommandsFactory {

  private static final String DEBUG_OPTION = "--debug";

  private final List<Seed4JCommand> seed4JCommands;
  private final Seed4JVersionProvider versionProvider;

  public Seed4JCommandsFactory(List<Seed4JCommand> seed4JCommands, Seed4JVersionProvider versionProvider) {
    this.seed4JCommands = seed4JCommands;
    this.versionProvider = versionProvider;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.create().name("seed4j").mixinStandardHelpOptions(true).versionProvider(versionProvider);
    spec.addOption(
      OptionSpec.builder(DEBUG_OPTION)
        .description("Enable runtime bootstrap diagnostics (extension mode only)")
        .type(Boolean.class)
        .defaultValue("false")
        .build()
    );

    spec.usageMessage().description("Seed4J CLI").headerHeading("%n").commandListHeading("%nCommands:%n");

    seed4JCommands.forEach(command -> spec.addSubcommand(command.name(), command.spec()));

    return spec;
  }
}
