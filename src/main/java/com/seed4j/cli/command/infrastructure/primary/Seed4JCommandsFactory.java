package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.DistributionMetadataApplicationService;
import java.util.List;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

@Component
class Seed4JCommandsFactory {

  private static final String DEBUG_OPTION = "--debug";

  private final List<Seed4JCommand> seed4JCommands;
  private final Seed4JVersionProvider versionProvider;
  private final DistributionMetadataApplicationService distribution;

  public Seed4JCommandsFactory(
    List<Seed4JCommand> seed4JCommands,
    Seed4JVersionProvider versionProvider,
    DistributionMetadataApplicationService distribution
  ) {
    this.seed4JCommands = seed4JCommands;
    this.versionProvider = versionProvider;
    this.distribution = distribution;
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

    spec.usageMessage().description(description()).headerHeading("%n").commandListHeading("%nCommands:%n");

    seed4JCommands.forEach(command -> spec.addSubcommand(command.name(), command.spec()));

    return spec;
  }

  private String description() {
    if (!distribution.metadata().identity().channel().experimental()) {
      return "Seed4J CLI";
    }

    return """
    Seed4J CLI
    WARNING: EXPERIMENTAL distribution; tracks Seed4J main through an unofficial snapshot that can expire.
    Restore stable use with: npm install -g seed4j-cli@latest""";
  }
}
