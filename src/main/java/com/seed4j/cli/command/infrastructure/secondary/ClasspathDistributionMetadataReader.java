package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.distribution.DistributionIdentity;
import com.seed4j.cli.command.domain.distribution.DistributionMetadata;
import com.seed4j.cli.command.domain.distribution.DistributionMetadataReader;
import com.seed4j.cli.command.domain.distribution.DistributionModuleSlug;
import com.seed4j.cli.command.domain.distribution.ReleaseChannel;
import com.seed4j.cli.command.domain.distribution.Seed4JDependencyCoordinate;
import com.seed4j.cli.command.domain.distribution.Seed4JModuleAvailability;
import com.seed4j.cli.command.domain.distribution.Seed4JUpstreamCommit;
import com.seed4j.cli.shared.error.domain.Assert;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
class ClasspathDistributionMetadataReader implements DistributionMetadataReader {

  private static final String RESOURCE = "classpath:META-INF/seed4j-cli-distribution.properties";
  private static final String RELEASE_CHANNEL = "release-channel";
  private static final String DEPENDENCY_COORDINATE = "seed4j-dependency-coordinate";
  private static final String LEGACY_UPSTREAM_COMMIT = "seed4j-upstream-commit";
  private static final String UNAVAILABLE_MODULES = "unavailable-modules";
  private static final Pattern FULL_SHA_SNAPSHOT_VERSION = Pattern.compile(
    "^\\d+\\.\\d+\\.\\d+-main\\.\\d{8}\\.\\d{6}\\.([0-9a-f]{40})-SNAPSHOT$"
  );

  private final ResourceLoader resourceLoader;

  ClasspathDistributionMetadataReader(ResourceLoader resourceLoader) {
    Assert.notNull("resourceLoader", resourceLoader);
    this.resourceLoader = resourceLoader;
  }

  @Override
  public DistributionMetadata read() {
    try {
      Resource resource = resourceLoader.getResource(RESOURCE);
      if (!resource.exists()) {
        return DistributionMetadata.stable();
      }

      return metadata(properties(resource));
    } catch (IOException | RuntimeException _) {
      return DistributionMetadata.stable();
    }
  }

  private static Properties properties(Resource resource) throws IOException {
    Properties properties = new Properties();
    try (InputStream input = resource.getInputStream()) {
      properties.load(input);
    }
    return properties;
  }

  private static DistributionMetadata metadata(Properties properties) {
    requireAvailabilityMetadata(properties);
    if (properties.containsKey(LEGACY_UPSTREAM_COMMIT)) {
      throw new IllegalArgumentException("Legacy upstream commit metadata is not supported");
    }
    ReleaseChannel channel = channel(properties.getProperty(RELEASE_CHANNEL));
    Seed4JDependencyCoordinate dependencyCoordinate = dependencyCoordinate(properties.getProperty(DEPENDENCY_COORDINATE));
    Optional<Seed4JUpstreamCommit> upstreamCommit = upstreamCommit(channel, dependencyCoordinate);
    return new DistributionMetadata(
      new DistributionIdentity(channel, dependencyCoordinate, upstreamCommit),
      new Seed4JModuleAvailability(unavailableModules(properties.getProperty(UNAVAILABLE_MODULES)))
    );
  }

  private static void requireAvailabilityMetadata(Properties properties) {
    if (!properties.containsKey(UNAVAILABLE_MODULES)) {
      throw new IllegalArgumentException("Missing module availability metadata");
    }
  }

  private static ReleaseChannel channel(String value) {
    return switch (value) {
      case "stable" -> ReleaseChannel.STABLE;
      case "experimental" -> ReleaseChannel.EXPERIMENTAL;
      case null, default -> throw new IllegalArgumentException("Unknown release channel");
    };
  }

  private static Seed4JDependencyCoordinate dependencyCoordinate(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Missing Seed4J dependency coordinate");
    }
    String[] segments = value.split(":", -1);
    if (segments.length != 3) {
      throw new IllegalArgumentException("Invalid Seed4J dependency coordinate");
    }
    return Seed4JDependencyCoordinate.versioned(segments[0].trim(), segments[1].trim(), segments[2].trim());
  }

  private static Optional<Seed4JUpstreamCommit> upstreamCommit(ReleaseChannel channel, Seed4JDependencyCoordinate dependencyCoordinate) {
    if (!channel.experimental()) {
      return Optional.empty();
    }

    String version = dependencyCoordinate.version().orElseThrow().value();
    Matcher fullVersion = FULL_SHA_SNAPSHOT_VERSION.matcher(version);
    if (fullVersion.matches()) {
      return Optional.of(new Seed4JUpstreamCommit(fullVersion.group(1)));
    }
    throw new IllegalArgumentException("Experimental version does not carry valid upstream provenance");
  }

  private static Set<DistributionModuleSlug> unavailableModules(String value) {
    if (value.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(value.split(",")).map(String::trim).map(DistributionModuleSlug::new).collect(Collectors.toUnmodifiableSet());
  }
}
