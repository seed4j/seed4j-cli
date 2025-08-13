package com.seed4j.cli.command.infrastructure.primary;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class Seed4JCommandsFactory {

  private final List<Seed4JCommand> seed4JCommands;
  private final String version;
  private final String jHLiteVersion;

  public Seed4JCommandsFactory(
    List<Seed4JCommand> seed4JCommands,
    @Value("${project.version}") String version,
    @Value("${project.jhlite-version}") String jHLiteVersion
  ) {
    this.seed4JCommands = seed4JCommands;
    this.version = version;
    this.jHLiteVersion = jHLiteVersion;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.create()
      .name("seed4j")
      .mixinStandardHelpOptions(true)
      .version(
        """
        Seed4J CLI v%s
        Seed4J version: %s""".formatted(version, jHLiteVersion)
      );

    spec.usageMessage().description("Seed4J CLI").headerHeading("%n").commandListHeading("%nCommands:%n");

    seed4JCommands.forEach(command -> spec.addSubcommand(command.name(), command.spec()));

    return spec;
  }
}
