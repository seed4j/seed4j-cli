package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.bootstrap.RuntimeMode;
import com.seed4j.cli.bootstrap.RuntimeSelection;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class Seed4JCommandsFactory {

  private final List<Seed4JCommand> seed4JCommands;
  private final String version;
  private final String seed4JVersion;
  private RuntimeSelection runtimeSelection;

  public Seed4JCommandsFactory(
    List<Seed4JCommand> seed4JCommands,
    @Value("${project.version}") String version,
    @Value("${project.seed4j-version}") String seed4JVersion
  ) {
    this.seed4JCommands = seed4JCommands;
    this.version = version;
    this.seed4JVersion = seed4JVersion;
    this.runtimeSelection = new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());
  }

  Seed4JCommandsFactory withRuntimeSelection(RuntimeSelection runtimeSelection) {
    this.runtimeSelection = runtimeSelection;
    return this;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.create().name("seed4j").mixinStandardHelpOptions(true).version(versionOutput());

    spec.usageMessage().description("Seed4J CLI").headerHeading("%n").commandListHeading("%nCommands:%n");

    seed4JCommands.forEach(command -> spec.addSubcommand(command.name(), command.spec()));

    return spec;
  }

  private String versionOutput() {
    return """
    Seed4J CLI v%s
    Seed4J version: %s
    Runtime mode: %s
    Distribution ID: %s
    Distribution version: %s""".formatted(
        version,
        seed4JVersion,
        runtimeSelection.mode().name().toLowerCase(),
        runtimeSelection.distributionId().orElse("standard"),
        runtimeSelection.distributionVersion().orElse(version)
      );
  }
}
