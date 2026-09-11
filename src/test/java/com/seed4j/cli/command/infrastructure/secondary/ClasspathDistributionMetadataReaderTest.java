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
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

@UnitTest
class ClasspathDistributionMetadataReaderTest {

  @Test
  void shouldReadPackagedStableDistributionMetadata() {
    ClasspathDistributionMetadataReader reader = new ClasspathDistributionMetadataReader(new DefaultResourceLoader());

    DistributionMetadata metadata = reader.read();

    assertThat(metadata.identity().channel()).isEqualTo(ReleaseChannel.STABLE);
    assertThat(metadata.identity().dependencyCoordinate()).isEqualTo(Seed4JDependencyCoordinate.versioned("com.seed4j", "seed4j", "2.2.0"));
    assertThat(metadata.identity().upstreamCommit()).isEmpty();
    assertThat(metadata.moduleAvailability().available("seed4j-extension")).isTrue();
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

  @Test
  void shouldReadExperimentalDistributionIdentityAndAvailability() {
    String experimentalMetadata = """
    release-channel=experimental
    seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:snapshot-version
    seed4j-upstream-commit=0123456789abcdef0123456789abcdef01234567
    unavailable-modules=seed4j-extension
    """;
    ClasspathDistributionMetadataReader reader = readerFor(experimentalMetadata);

    DistributionMetadata metadata = reader.read();

    assertThat(metadata.identity().channel()).isEqualTo(ReleaseChannel.EXPERIMENTAL);
    assertThat(metadata.identity().dependencyCoordinate()).isEqualTo(
      Seed4JDependencyCoordinate.versioned("io.github.renanfranca", "seed4j-main-snapshot", "snapshot-version")
    );
    assertThat(metadata.identity().upstreamCommit()).hasValueSatisfying(commit ->
      assertThat(commit.value()).isEqualTo("0123456789abcdef0123456789abcdef01234567")
    );
    assertThat(metadata.moduleAvailability().unavailableModules()).isEqualTo(Set.of(new DistributionModuleSlug("seed4j-extension")));
  }

  private static Stream<Arguments> malformedMetadata() {
    return Stream.of(
      Arguments.of(
        Named.of(
          "missing availability",
          """
          release-channel=experimental
          seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:snapshot-version
          seed4j-upstream-commit=0123456789abcdef0123456789abcdef01234567
          """
        )
      ),
      Arguments.of(
        Named.of(
          "extension available experimentally",
          """
          release-channel=experimental
          seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:snapshot-version
          seed4j-upstream-commit=0123456789abcdef0123456789abcdef01234567
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
          seed4j-upstream-commit=0123456789abcdef0123456789abcdef01234567
          unavailable-modules=seed4j-extension
          """
        )
      ),
      Arguments.of(
        Named.of(
          "missing dependency coordinate",
          """
          release-channel=experimental
          seed4j-upstream-commit=0123456789abcdef0123456789abcdef01234567
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
          seed4j-upstream-commit=0123456789abcdef0123456789abcdef01234567
          unavailable-modules=seed4j-extension
          """
        )
      ),
      Arguments.of(
        Named.of(
          "invalid upstream commit",
          """
          release-channel=experimental
          seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:snapshot-version
          seed4j-upstream-commit=not-a-sha
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
          seed4j-upstream-commit=
          unavailable-modules=seed4j-extension
          """
        )
      ),
      Arguments.of(
        Named.of(
          "more than extension unavailable experimentally",
          """
          release-channel=experimental
          seed4j-dependency-coordinate=io.github.renanfranca:seed4j-main-snapshot:snapshot-version
          seed4j-upstream-commit=0123456789abcdef0123456789abcdef01234567
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
