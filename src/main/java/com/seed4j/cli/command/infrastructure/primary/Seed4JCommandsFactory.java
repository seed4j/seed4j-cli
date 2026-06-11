package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.RuntimeDisplayApplicationService;
import com.seed4j.cli.command.domain.RuntimeDisplay;
import com.seed4j.cli.command.domain.RuntimeDistributionId;
import com.seed4j.cli.command.domain.RuntimeDistributionVersion;
import com.seed4j.cli.command.domain.RuntimeModeDisplay;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

@Component
class Seed4JCommandsFactory {

  private static final String DEBUG_OPTION = "--debug";
  private static final String UNKNOWN_VERSION = "unknown";

  private final List<Seed4JCommand> seed4JCommands;
  private final String projectCliVersion;
  private final String projectSeed4JVersion;
  private final RuntimeDisplayApplicationService runtimeDisplayApplicationService;

  public Seed4JCommandsFactory(
    List<Seed4JCommand> seed4JCommands,
    @Value("${project.version:}") String projectCliVersion,
    @Value("${project.seed4j-version:}") String projectSeed4JVersion,
    RuntimeDisplayApplicationService runtimeDisplayApplicationService
  ) {
    this.seed4JCommands = seed4JCommands;
    this.projectCliVersion = projectCliVersion;
    this.projectSeed4JVersion = projectSeed4JVersion;
    this.runtimeDisplayApplicationService = runtimeDisplayApplicationService;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.create().name("seed4j").mixinStandardHelpOptions(true).version(versionOutput());
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

  private String versionOutput() {
    String resolvedCliVersion = resolvedVersion(projectCliVersion, UNKNOWN_VERSION);
    String resolvedSeed4JVersion = resolvedVersion(projectSeed4JVersion, resolvedCliVersion);
    RuntimeDisplay runtimeDisplay = runtimeDisplayApplicationService.activeRuntime();

    String commonOutput = """
      Seed4J CLI v%s
      Seed4J version: %s
      Runtime mode: %s""".formatted(resolvedCliVersion, resolvedSeed4JVersion, runtimeDisplay.mode().name().toLowerCase());

    if (runtimeDisplay.mode() != RuntimeModeDisplay.EXTENSION) {
      return commonOutput;
    }

    return """
    %s
    Distribution ID: %s
    Distribution version: %s""".formatted(
      commonOutput,
      runtimeDisplay.distributionId().map(RuntimeDistributionId::value).orElse(UNKNOWN_VERSION),
      runtimeDisplay.distributionVersion().map(RuntimeDistributionVersion::value).orElse(UNKNOWN_VERSION)
    );
  }

  private static String resolvedVersion(String primaryValue, String defaultValue) {
    return nonBlank(primaryValue).orElse(defaultValue);
  }

  private static Optional<String> nonBlank(String candidateValue) {
    return Optional.ofNullable(candidateValue)
      .map(String::trim)
      .filter(value -> !value.isEmpty());
  }
}
