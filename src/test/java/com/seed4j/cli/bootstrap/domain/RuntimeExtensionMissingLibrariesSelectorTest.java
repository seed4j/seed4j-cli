package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.seed4j.cli.UnitTest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@UnitTest
class RuntimeExtensionMissingLibrariesSelectorTest {

  @Test
  void shouldReportExplicitNonComparableVersionReasonWhenFailingFast() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      new RuntimeLibraryEntry("shared-lib-2.0.0.jar", Optional.of(new RuntimeLibraryIdentity("com.acme:shared-lib", "2.0.0")))
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      new RuntimeLibraryEntry("shared-lib-RELEASE.jar", Optional.of(new RuntimeLibraryIdentity("com.acme:shared-lib", "RELEASE")))
    );

    assertThatThrownBy(() -> new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("com.acme:shared-lib")
      .hasMessageContaining("RELEASE")
      .hasMessageContaining("2.0.0")
      .hasMessageContaining("not safely comparable");
  }

  @Test
  void shouldLogDebugDecisionWhenCliVersionWinsOverOlderExtensionVersion() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      new RuntimeLibraryEntry(
        "logback-classic-1.5.22.jar",
        Optional.of(new RuntimeLibraryIdentity("ch.qos.logback:logback-classic", "1.5.22"))
      )
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      new RuntimeLibraryEntry(
        "logback-classic-1.5.32.jar",
        Optional.of(new RuntimeLibraryIdentity("ch.qos.logback:logback-classic", "1.5.32"))
      )
    );
    Logger logger = (Logger) LoggerFactory.getLogger(RuntimeExtensionMissingLibrariesSelector.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);

    try {
      new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list)
      .extracting(ILoggingEvent::getFormattedMessage)
      .anyMatch(message -> message.contains("ch.qos.logback:logback-classic") && message.contains("1.5.32") && message.contains("1.5.22"));
  }

  @Test
  void shouldKeepCliRuntimeLibraryWhenVersionsDifferOnlyByTrailingZeroSegments() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      new RuntimeLibraryEntry("shared-lib-1.2.jar", Optional.of(new RuntimeLibraryIdentity("com.acme:shared-lib", "1.2")))
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      new RuntimeLibraryEntry("shared-lib-1.2.0.jar", Optional.of(new RuntimeLibraryIdentity("com.acme:shared-lib", "1.2.0")))
    );

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).isEmpty();
  }

  @Test
  void shouldFailFastWhenCliVersionIsNonComparableAndExtensionRequiresNumericVersion() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      new RuntimeLibraryEntry("shared-lib-2.0.0.jar", Optional.of(new RuntimeLibraryIdentity("com.acme:shared-lib", "2.0.0")))
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      new RuntimeLibraryEntry("shared-lib-RELEASE.jar", Optional.of(new RuntimeLibraryIdentity("com.acme:shared-lib", "RELEASE")))
    );

    assertThatThrownBy(() -> new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("com.acme:shared-lib")
      .hasMessageContaining("RELEASE")
      .hasMessageContaining("2.0.0");
  }

  @Test
  void shouldFailFastWhenExtensionVersionTokenIsUppercaseVAndNonComparable() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      new RuntimeLibraryEntry(
        "shared-lib-V999999999999999999999999999999.jar",
        Optional.of(new RuntimeLibraryIdentity("com.acme:shared-lib", "V999999999999999999999999999999"))
      )
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      new RuntimeLibraryEntry("shared-lib-1.0.0.jar", Optional.of(new RuntimeLibraryIdentity("com.acme:shared-lib", "1.0.0")))
    );

    assertThatThrownBy(() -> new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("com.acme:shared-lib")
      .hasMessageContaining("1.0.0")
      .hasMessageContaining("V999999999999999999999999999999");
  }

  @Test
  void shouldKeepCliRuntimeLibraryWhenExtensionUsesOlderVersionForSameCoordinate() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      new RuntimeLibraryEntry(
        "logback-classic-1.5.22.jar",
        Optional.of(new RuntimeLibraryIdentity("ch.qos.logback:logback-classic", "1.5.22"))
      )
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      new RuntimeLibraryEntry(
        "logback-classic-1.5.32.jar",
        Optional.of(new RuntimeLibraryIdentity("ch.qos.logback:logback-classic", "1.5.32"))
      )
    );

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).isEmpty();
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
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      new RuntimeLibraryEntry("extension-shaded.jar", Optional.of(new RuntimeLibraryIdentity("com.acme:shared-lib", "1.0.0")))
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      new RuntimeLibraryEntry("cli-renamed.jar", Optional.of(new RuntimeLibraryIdentity("com.acme:shared-lib", "1.0.0")))
    );

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).isEmpty();
  }

  @Test
  void shouldClassifyLibraryAsMissingWhenIdentityDiffersWithoutCoordinateVersionConflict() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(
      new RuntimeLibraryEntry("bundle-all.jar", Optional.of(new RuntimeLibraryIdentity("com.extension:bundle", "2.0.0")))
    );
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      new RuntimeLibraryEntry("bundle-all.jar", Optional.of(new RuntimeLibraryIdentity("com.cli:bundle", "1.0.0")))
    );

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
      .hasMessageContaining("shared-lib");
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
}
