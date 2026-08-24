package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.RuntimeDisplayApplicationService;
import com.seed4j.cli.command.domain.RuntimeDisplay;
import com.seed4j.cli.command.domain.RuntimeDistributionId;
import com.seed4j.cli.command.domain.RuntimeDistributionVersion;
import com.seed4j.cli.command.domain.RuntimeModeDisplay;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine.IVersionProvider;

@Component
class Seed4JVersionProvider implements IVersionProvider {

  private static final String UNKNOWN_VERSION = "unknown";

  private final String projectCliVersion;
  private final String projectSeed4JVersion;
  private final RuntimeDisplayApplicationService runtimeDisplayApplicationService;

  Seed4JVersionProvider(
    @Value("${project.version:}") String projectCliVersion,
    @Value("${project.seed4j-version:}") String projectSeed4JVersion,
    RuntimeDisplayApplicationService runtimeDisplayApplicationService
  ) {
    this.projectCliVersion = projectCliVersion;
    this.projectSeed4JVersion = projectSeed4JVersion;
    this.runtimeDisplayApplicationService = runtimeDisplayApplicationService;
  }

  @Override
  public String[] getVersion() {
    return new String[] { versionOutput() };
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
