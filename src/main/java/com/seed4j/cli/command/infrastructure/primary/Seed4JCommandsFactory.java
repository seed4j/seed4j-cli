package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class Seed4JCommandsFactory {

  private static final String CLI_VERSION_PROPERTY = "seed4j.cli.version";
  private static final String SEED4J_VERSION_PROPERTY = "seed4j.cli.seed4j.version";
  private static final String UNKNOWN_VERSION = "unknown";

  private final List<Seed4JCommand> seed4JCommands;
  private final String version;
  private final String seed4JVersion;
  private final RuntimeSelectionProvider runtimeSelectionProvider;
  private final RuntimeSystemProperties runtimeSystemProperties;

  public Seed4JCommandsFactory(
    List<Seed4JCommand> seed4JCommands,
    @Value("${project.version:}") String version,
    @Value("${project.seed4j-version:}") String seed4JVersion,
    RuntimeSelectionProvider runtimeSelectionProvider,
    RuntimeSystemProperties runtimeSystemProperties
  ) {
    this.seed4JCommands = seed4JCommands;
    this.version = version;
    this.seed4JVersion = seed4JVersion;
    this.runtimeSelectionProvider = runtimeSelectionProvider;
    this.runtimeSystemProperties = runtimeSystemProperties;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.create().name("seed4j").mixinStandardHelpOptions(true).version(versionOutput());

    spec.usageMessage().description("Seed4J CLI").headerHeading("%n").commandListHeading("%nCommands:%n");

    seed4JCommands.forEach(command -> spec.addSubcommand(command.name(), command.spec()));

    return spec;
  }

  private String versionOutput() {
    RuntimeSelection runtimeSelection = runtimeSelectionProvider.runtimeSelection();
    Map<String, String> systemProperties = runtimeSystemProperties.values();
    String resolvedCliVersion = resolvedVersion(systemProperties.get(CLI_VERSION_PROPERTY), version, UNKNOWN_VERSION);
    String resolvedSeed4JVersion = resolvedVersion(systemProperties.get(SEED4J_VERSION_PROPERTY), seed4JVersion, resolvedCliVersion);

    return """
    Seed4J CLI v%s
    Seed4J version: %s
    Runtime mode: %s
    Distribution ID: %s
    Distribution version: %s""".formatted(
        resolvedCliVersion,
        resolvedSeed4JVersion,
        runtimeSelection.mode().name().toLowerCase(),
        runtimeSelection.distributionId().orElse("standard"),
        runtimeSelection.distributionVersion().orElse(resolvedCliVersion)
      );
  }

  private static String resolvedVersion(String prioritizedValue, String fallbackValue, String defaultValue) {
    return nonBlank(prioritizedValue)
      .or(() -> nonBlank(fallbackValue))
      .orElse(defaultValue);
  }

  private static Optional<String> nonBlank(String candidateValue) {
    return Optional.ofNullable(candidateValue)
      .map(String::trim)
      .filter(value -> !value.isEmpty());
  }
}
