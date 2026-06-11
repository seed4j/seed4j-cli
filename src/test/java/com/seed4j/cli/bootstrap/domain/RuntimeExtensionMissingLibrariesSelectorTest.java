package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class RuntimeExtensionMissingLibrariesSelectorTest {

  @ParameterizedTest
  @ValueSource(strings = { "v1.2.0", "V1.2.0" })
  void shouldKeepCliRuntimeLibraryWhenVersionMatchesAfterNormalizingVPrefix(String extensionVersion) {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      libraryEntry("shared-lib-" + extensionVersion + ".jar", "com.acme:shared-lib", extensionVersion)
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(libraryEntry("shared-lib-1.2.0.jar", "com.acme:shared-lib", "1.2.0"));

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).isEmpty();
  }

  @Test
  void shouldKeepCliRuntimeLibraryWhenExtensionUsesOlderNumericVersionForSameCoordinate() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      libraryEntry("logback-classic-1.5.22.jar", "ch.qos.logback:logback-classic", "1.5.22")
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(libraryEntry("logback-classic-1.5.32.jar", "ch.qos.logback:logback-classic", "1.5.32"));

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).isEmpty();
  }

  @Test
  void shouldKeepCliRuntimeLibraryWhenVersionsDifferOnlyByTrailingZeroSegments() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(libraryEntry("shared-lib-1.2.jar", "com.acme:shared-lib", "1.2"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(libraryEntry("shared-lib-1.2.0.jar", "com.acme:shared-lib", "1.2.0"));

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).isEmpty();
  }

  @Test
  void shouldKeepCliRuntimeLibraryWhenExtensionUsesOlderFinalQualifiedVersionForSameCoordinate() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(libraryEntry("hibernate-core-7.2.0.Final.jar", "hibernate-core", "7.2.0.Final"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(libraryEntry("hibernate-core-7.2.12.final.jar", "hibernate-core", "7.2.12.final"));

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).isEmpty();
  }

  @Test
  void shouldNotFailWhenCoordinateHasSameNonNumericVersionInCliAndExtension() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      libraryEntry("org.eclipse.jgit-7.5.0.202512021534-r.jar", "org.eclipse.jgit:org.eclipse.jgit", "7.5.0.202512021534-r")
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      libraryEntry("org.eclipse.jgit-7.5.0.202512021534-r.jar", "org.eclipse.jgit:org.eclipse.jgit", "7.5.0.202512021534-r")
    );

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("notSafelyComparableVersionScenarios")
  void shouldFailFastWhenVersionsAreNotSafelyComparable(String scenarioName, NotSafelyComparableVersionScenario scenario) {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      libraryEntry(scenario.extensionFileName(), scenario.coordinate(), scenario.extensionVersion())
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(libraryEntry(scenario.cliFileName(), scenario.coordinate(), scenario.cliVersion()));

    assertThatThrownBy(() -> new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining(scenario.coordinate())
      .hasMessageContaining(scenario.cliVersion())
      .hasMessageContaining(scenario.extensionVersionSnippet())
      .hasMessageContaining("not safely comparable");
  }

  private static Stream<Arguments> notSafelyComparableVersionScenarios() {
    String malformedNumericVersion = "1.".repeat(2500);
    String malformedQualifiedVersion = "1.".repeat(2500) + "1-";

    return Stream.of(
      Arguments.of(
        "release token cannot be compared with numeric version",
        new NotSafelyComparableVersionScenario(
          "shared-lib-2.0.0.jar",
          "com.acme:shared-lib",
          "2.0.0",
          "shared-lib-RELEASE.jar",
          "RELEASE",
          "2.0.0"
        )
      ),
      Arguments.of(
        "numeric segment outside integer range cannot be compared",
        new NotSafelyComparableVersionScenario(
          "shared-lib-999999999999999999999999999.jar",
          "com.acme:shared-lib",
          "999999999999999999999999999",
          "shared-lib-1.0.0.jar",
          "1.0.0",
          "999999999999999999999999999"
        )
      ),
      Arguments.of(
        "qualified version cannot be compared with release token",
        new NotSafelyComparableVersionScenario(
          "hibernate-core-7.2.12.Final.jar",
          "org.hibernate.orm:hibernate-core",
          "7.2.12.Final",
          "hibernate-core-RELEASE.jar",
          "RELEASE",
          "7.2.12.Final"
        )
      ),
      Arguments.of(
        "qualified versions with different qualifier families cannot be compared",
        new NotSafelyComparableVersionScenario(
          "hibernate-core-7.2.12.Final.jar",
          "org.hibernate.orm:hibernate-core",
          "7.2.12.Final",
          "hibernate-core-7.2.13.CR.jar",
          "7.2.13.CR",
          "7.2.12.Final"
        )
      ),
      Arguments.of(
        "qualified version with numeric segment outside integer range cannot be compared",
        new NotSafelyComparableVersionScenario(
          "shared-lib-999999999999999999999999999.Final.jar",
          "com.acme:shared-lib",
          "999999999999999999999999999.Final",
          "shared-lib-1.0.0-final.jar",
          "1.0.0-final",
          "999999999999999999999999999.Final"
        )
      ),
      Arguments.of(
        "malformed numeric version cannot be compared",
        new NotSafelyComparableVersionScenario(
          "shared-lib-malformed.jar",
          "com.acme:shared-lib",
          malformedNumericVersion,
          "shared-lib-1.0.0.jar",
          "1.0.0",
          "1."
        )
      ),
      Arguments.of(
        "malformed qualified version cannot be compared",
        new NotSafelyComparableVersionScenario(
          "shared-lib-malformed-qualified.jar",
          "com.acme:shared-lib",
          malformedQualifiedVersion,
          "shared-lib-1.0.0-final.jar",
          "1.0.0-final",
          "1."
        )
      )
    );
  }

  @Test
  void shouldFailUsingFirstConflictingExtensionLibraryWhenMultipleConflictsExist() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      RuntimeLibraryEntry.fromFileName("shared-lib-2.0.0.jar"),
      RuntimeLibraryEntry.fromFileName("bundle-all.jar")
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      RuntimeLibraryEntry.fromFileName("shared-lib-1.0.0.jar"),
      RuntimeLibraryEntry.fromFileName("bundle-all.jar")
    );

    assertThatThrownBy(() -> new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("shared-lib")
      .hasMessageContaining("1.0.0")
      .hasMessageContaining("2.0.0");
  }

  @Test
  void shouldClassifyLibraryAsPresentWhenIdentityMatchesCliEvenWithDifferentFileName() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(libraryEntry("extension-shaded.jar", "com.acme:shared-lib", "1.0.0"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(libraryEntry("cli-renamed.jar", "com.acme:shared-lib", "1.0.0"));

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).isEmpty();
  }

  @Test
  void shouldClassifyLibraryAsMissingWhenIdentityDiffersWithoutCoordinateVersionConflict() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(libraryEntry("bundle-all.jar", "com.extension:bundle", "2.0.0"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(libraryEntry("bundle-all.jar", "com.cli:bundle", "1.0.0"));

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).containsExactly("bundle-all.jar");
  }

  @Test
  void shouldClassifyLibraryAsConflictWhenIdentityIsMissingAndFileNameCollidesWithCli() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(RuntimeLibraryEntry.fromFileName("bundle-all.jar"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(RuntimeLibraryEntry.fromFileName("bundle-all.jar"));

    assertThatThrownBy(() -> new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("bundle-all.jar");
  }

  @Test
  void shouldNotFailFastWhenExtensionLibraryUsesClassifierSuffixAndCliHasBaseVersion() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(RuntimeLibraryEntry.fromFileName("shared-lib-1.0.0-jdk17.jar"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(RuntimeLibraryEntry.fromFileName("shared-lib-1.0.0.jar"));

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).containsExactly("shared-lib-1.0.0-jdk17.jar");
  }

  @Test
  void shouldFailFastWhenExtensionLibraryUsesReleaseVersionTokenAndCliHasDifferentVersion() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(RuntimeLibraryEntry.fromFileName("shared-lib-RELEASE.jar"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(RuntimeLibraryEntry.fromFileName("shared-lib-1.0.0.jar"));

    assertThatThrownBy(() -> new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("shared-lib");
  }

  @Test
  void shouldFailFastWhenExtensionLibraryUsesVPrefixedVersionAndCliHasDifferentVersion() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(RuntimeLibraryEntry.fromFileName("shared-lib-v2.0.0.jar"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(RuntimeLibraryEntry.fromFileName("shared-lib-1.0.0.jar"));

    assertThatThrownBy(() -> new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("shared-lib");
  }

  @Test
  void shouldReturnOnlyExtensionLibrariesThatAreMissingFromCliPreservingExtensionOrder() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      RuntimeLibraryEntry.fromFileName("shared-lib-1.0.0.jar"),
      RuntimeLibraryEntry.fromFileName("missing-lib-2.0.0.jar"),
      RuntimeLibraryEntry.fromFileName("another-missing-lib-3.1.0.jar")
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      RuntimeLibraryEntry.fromFileName("shared-lib-1.0.0.jar"),
      RuntimeLibraryEntry.fromFileName("seed4j-core-9.9.9.jar")
    );

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).containsExactly("missing-lib-2.0.0.jar", "another-missing-lib-3.1.0.jar");
  }

  @Test
  void shouldClassifyLibraryAsConflictWhenCoordinateMatchesCliWithDifferentVersion() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(RuntimeLibraryEntry.fromFileName("shared-lib-2.0.0.jar"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(RuntimeLibraryEntry.fromFileName("shared-lib-1.0.0.jar"));

    assertThatThrownBy(() -> new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("shared-lib")
      .hasMessageContaining("1.0.0")
      .hasMessageContaining("2.0.0");
  }

  @Test
  void shouldFailFastWhenCliContainsSameCoordinateWithDifferentVersions() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(RuntimeLibraryEntry.fromFileName("extension-only-lib-9.0.0.jar"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      RuntimeLibraryEntry.fromFileName("shared-lib-1.0.0.jar"),
      RuntimeLibraryEntry.fromFileName("shared-lib-2.0.0.jar")
    );

    assertThatThrownBy(() -> new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("shared-lib")
      .hasMessageContaining("1.0.0")
      .hasMessageContaining("2.0.0");
  }

  @Test
  void shouldClassifyLibraryAsMissingWhenIdentityIsMissingAndFileNameDoesNotCollideWithCli() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(RuntimeLibraryEntry.fromFileName("shared-lib-custom.jar"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(RuntimeLibraryEntry.fromFileName("shared-lib-1.0.0.jar"));

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).containsExactly("shared-lib-custom.jar");
  }

  private static RuntimeLibraryEntry libraryEntry(String fileName, String coordinate, String version) {
    return new RuntimeLibraryEntry(
      new RuntimeLibraryFileName(fileName),
      Optional.of(new RuntimeLibraryIdentity(new RuntimeLibraryCoordinate(coordinate), new RuntimeLibraryVersion(version)))
    );
  }

  private record NotSafelyComparableVersionScenario(
    String extensionFileName,
    String coordinate,
    String extensionVersion,
    String cliFileName,
    String cliVersion,
    String extensionVersionSnippet
  ) {}
}
