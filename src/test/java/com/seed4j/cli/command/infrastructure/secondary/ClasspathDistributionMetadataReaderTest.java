package com.seed4j.cli.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.domain.distribution.DistributionMetadata;
import com.seed4j.cli.command.domain.distribution.DistributionModuleSlug;
import com.seed4j.cli.command.domain.distribution.ReleaseChannel;
import com.seed4j.cli.command.domain.distribution.Seed4JDependencyCoordinate;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

@UnitTest
class ClasspathDistributionMetadataReaderTest {

  private static final String UPSTREAM_SHA = "0123456789abcdef0123456789abcdef01234567";
  private static final String FULL_SHA_VERSION = "2.2.1-main.20260907.055800." + UPSTREAM_SHA + "-SNAPSHOT";

  @Test
  void shouldReadPackagedDistributionMetadata() throws Exception {
    Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of("pom.xml").toFile());
    Element properties = (Element) pom.getElementsByTagName("properties").item(0);
    assertThat(properties).as("pom.xml properties").isNotNull();
    String channel = expectedProperty(properties, "seed4j.release-channel");
    String groupId = expectedProperty(properties, "seed4j.group-id");
    String artifactId = expectedProperty(properties, "seed4j.artifact-id");
    String version = expectedProperty(properties, "seed4j.version");
    ReleaseChannel expectedChannel = switch (channel) {
      case "experimental" -> ReleaseChannel.EXPERIMENTAL;
      case "stable" -> ReleaseChannel.STABLE;
      default -> throw new IllegalStateException("Unknown seed4j.release-channel: " + channel);
    };
    Matcher expectedSha = Pattern.compile("\\.([0-9a-fA-F]{40})-SNAPSHOT$").matcher(version);
    if (expectedChannel == ReleaseChannel.EXPERIMENTAL) {
      assertThat(expectedSha.find()).as("experimental seed4j.version SHA suffix").isTrue();
    }
    ClasspathDistributionMetadataReader reader = new ClasspathDistributionMetadataReader(new DefaultResourceLoader());

    DistributionMetadata metadata = reader.read();

    assertThat(metadata.identity().channel()).isEqualTo(expectedChannel);
    assertThat(metadata.identity().dependencyCoordinate()).isEqualTo(Seed4JDependencyCoordinate.versioned(groupId, artifactId, version));
    if (expectedChannel == ReleaseChannel.EXPERIMENTAL) {
      assertThat(metadata.identity().upstreamCommit()).hasValueSatisfying(commit ->
        assertThat(commit.value()).isEqualTo(expectedSha.group(1))
      );
      assertThat(metadata.moduleAvailability().unavailableModules()).isEqualTo(Set.of(new DistributionModuleSlug("seed4j-extension")));
      return;
    }

    assertThat(metadata.identity().upstreamCommit()).isEmpty();
    assertThat(metadata.moduleAvailability().unavailableModules()).isEmpty();
  }

  private static String expectedProperty(Element properties, String name) {
    Node property = properties.getElementsByTagName(name).item(0);
    String value = System.getProperty(name, property == null ? null : property.getTextContent());
    assertThat(value)
      .as("required Maven property " + name)
      .isNotBlank();
    return value;
  }

  @Test
  void shouldUseStableDistributionDefaultsWhenPackagedMetadataIsAbsent() {
    ClassLoader emptyClassLoader = new URLClassLoader(new URL[0], null);
    ClasspathDistributionMetadataReader reader = new ClasspathDistributionMetadataReader(new DefaultResourceLoader(emptyClassLoader));

    DistributionMetadata metadata = reader.read();

    assertThat(metadata.identity().channel()).isEqualTo(ReleaseChannel.STABLE);
    assertThat(metadata.identity().dependencyCoordinate()).isEqualTo(Seed4JDependencyCoordinate.unversioned("com.seed4j", "seed4j"));
    assertThat(metadata.identity().upstreamCommit()).isEmpty();
    assertThat(metadata.moduleAvailability().available("seed4j-extension")).isTrue();
  }

  @ParameterizedTest
  @MethodSource("malformedMetadata")
  void shouldUseStableDistributionDefaultsWhenPackagedMetadataIsMalformed(String malformedMetadata) {
    ClasspathDistributionMetadataReader reader = readerFor(malformedMetadata);

    DistributionMetadata metadata = reader.read();

    assertThat(metadata).isEqualTo(DistributionMetadata.stable());
  }

  @ParameterizedTest
  @MethodSource("validExperimentalVersions")
  void shouldReadExperimentalDistributionIdentityAndAvailability(String version, String sha) {
    String experimentalMetadata = """
    release-channel=experimental
    seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:%s
    unavailable-modules=seed4j-extension
    """.formatted(version);
    ClasspathDistributionMetadataReader reader = readerFor(experimentalMetadata);

    DistributionMetadata metadata = reader.read();

    assertThat(metadata.identity().channel()).isEqualTo(ReleaseChannel.EXPERIMENTAL);
    assertThat(metadata.identity().dependencyCoordinate()).isEqualTo(
      Seed4JDependencyCoordinate.versioned("io.github.renanfranca", "seed4j-main-snapshot", version)
    );
    assertThat(metadata.identity().upstreamCommit()).hasValueSatisfying(commit -> assertThat(commit.value()).isEqualTo(sha));
    assertThat(metadata.moduleAvailability().unavailableModules()).isEqualTo(Set.of(new DistributionModuleSlug("seed4j-extension")));
  }

  private static Stream<Arguments> validExperimentalVersions() {
    return Stream.of(
      Arguments.of(FULL_SHA_VERSION, UPSTREAM_SHA),
      Arguments.of(
        "3.4.5-main.20261231.235959.abcdef0123456789abcdef0123456789abcdef01-SNAPSHOT",
        "abcdef0123456789abcdef0123456789abcdef01"
      )
    );
  }

  @Test
  void shouldReadStableDistributionIdentityAndAvailability() {
    String stableMetadata = """
    release-channel=stable
    seed4j-dependency-coordinate=com.seed4j:seed4j:9.8.7
    unavailable-modules=
    """;
    ClasspathDistributionMetadataReader reader = readerFor(stableMetadata);

    DistributionMetadata metadata = reader.read();

    assertThat(metadata.identity().channel()).isEqualTo(ReleaseChannel.STABLE);
    assertThat(metadata.identity().dependencyCoordinate()).isEqualTo(Seed4JDependencyCoordinate.versioned("com.seed4j", "seed4j", "9.8.7"));
    assertThat(metadata.identity().upstreamCommit()).isEmpty();
    assertThat(metadata.moduleAvailability().unavailableModules()).isEmpty();
  }

  @Test
  void shouldRejectLegacyUpstreamCommitMetadata() {
    String experimentalMetadata = """
    release-channel=experimental
    seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:%s
    seed4j-upstream-commit=%s
    unavailable-modules=seed4j-extension
    """.formatted(FULL_SHA_VERSION, UPSTREAM_SHA);
    ClasspathDistributionMetadataReader reader = readerFor(experimentalMetadata);

    DistributionMetadata metadata = reader.read();

    assertThat(metadata).isEqualTo(DistributionMetadata.stable());
  }

  private static Stream<Arguments> malformedMetadata() {
    return Stream.of(
      Arguments.of(
        Named.of(
          "missing availability",
          """
          release-channel=experimental
          seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:snapshot-version
          """
        )
      ),
      Arguments.of(
        Named.of(
          "extension available experimentally",
          """
          release-channel=experimental
          seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:2.2.1-main.20260907.055800.0123456789abcdef0123456789abcdef01234567-SNAPSHOT
          unavailable-modules=
          """
        )
      ),
      Arguments.of(
        Named.of(
          "unknown channel",
          """
          release-channel=preview
          seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:snapshot-version
          unavailable-modules=seed4j-extension
          """
        )
      ),
      Arguments.of(
        Named.of(
          "missing dependency coordinate",
          """
          release-channel=experimental
          unavailable-modules=seed4j-extension
          """
        )
      ),
      Arguments.of(
        Named.of(
          "incomplete dependency coordinate",
          """
          release-channel=experimental
          seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot
          unavailable-modules=seed4j-extension
          """
        )
      ),
      Arguments.of(
        Named.of(
          "invalid upstream version",
          """
          release-channel=experimental
          seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:snapshot-version
          unavailable-modules=seed4j-extension
          """
        )
      ),
      Arguments.of(
        Named.of(
          "short SHA version",
          """
          release-channel=experimental
          seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:2.2.1-main.20260907.055800.0123456789ab-SNAPSHOT
          unavailable-modules=seed4j-extension
          """
        )
      ),
      Arguments.of(
        Named.of(
          "stable unavailable module",
          """
          release-channel=stable
          seed4j-dependency-coordinate=com.seed4j:seed4j:2.2.0
          unavailable-modules=seed4j-extension
          """
        )
      ),
      Arguments.of(
        Named.of(
          "more than extension unavailable experimentally",
          """
          release-channel=experimental
          seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:2.2.1-main.20260907.055800.0123456789abcdef0123456789abcdef01234567-SNAPSHOT
          unavailable-modules=seed4j-extension,another-module
          """
        )
      )
    );
  }

  private static ClasspathDistributionMetadataReader readerFor(String metadata) {
    return new ClasspathDistributionMetadataReader(
      new DefaultResourceLoader() {
        @Override
        public Resource getResource(String location) {
          return new ByteArrayResource(metadata.getBytes(StandardCharsets.UTF_8));
        }
      }
    );
  }
}
