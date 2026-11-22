package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.DistributionMetadataApplicationService;
import com.seed4j.cli.command.domain.distribution.DependencyVersion;
import com.seed4j.cli.command.domain.distribution.DistributionMetadata;
import com.seed4j.cli.command.domain.distribution.Seed4JUpstreamCommit;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine.IVersionProvider;

@Component
class Seed4JVersionProvider implements IVersionProvider {

  private static final String UNKNOWN_VERSION = "unknown";

  private final String projectCliVersion;
  private final String projectSeed4JVersion;
  private final DistributionMetadataApplicationService distribution;

  Seed4JVersionProvider(
    @Value("${project.version:}") String projectCliVersion,
    @Value("${project.seed4j-version:}") String projectSeed4JVersion,
    DistributionMetadataApplicationService distribution
  ) {
    this.projectCliVersion = projectCliVersion;
    this.projectSeed4JVersion = projectSeed4JVersion;
    this.distribution = distribution;
  }

  @Override
  public String[] getVersion() {
    return new String[] { versionOutput() };
  }

  private String versionOutput() {
    String resolvedCliVersion = resolvedVersion(projectCliVersion, UNKNOWN_VERSION);
    DistributionMetadata metadata = distribution.metadata();
    String resolvedSeed4JVersion = metadata
      .identity()
      .dependencyCoordinate()
      .version()
      .map(DependencyVersion::value)
      .orElseGet(() -> resolvedVersion(projectSeed4JVersion, resolvedCliVersion));
    if (metadata.identity().channel().experimental()) {
      return experimentalVersionOutput(resolvedCliVersion, resolvedSeed4JVersion, metadata);
    }

    return """
    Seed4J CLI v%s
    Seed4J version: %s
    Runtime mode: standard""".formatted(resolvedCliVersion, resolvedSeed4JVersion);
  }

  private static String experimentalVersionOutput(String cliVersion, String seed4JVersion, DistributionMetadata metadata) {
    return """
    Seed4J CLI v%s
    Release channel: experimental
    Seed4J version: %s
    Seed4J upstream commit: %s
    Runtime mode: standard""".formatted(
      cliVersion,
      seed4JVersion,
      metadata.identity().upstreamCommit().map(Seed4JUpstreamCommit::value).orElse(UNKNOWN_VERSION)
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
