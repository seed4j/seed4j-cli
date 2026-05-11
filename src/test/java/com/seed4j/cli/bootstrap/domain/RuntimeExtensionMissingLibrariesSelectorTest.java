package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionMissingLibrariesSelectorTest {

  @Test
  void shouldFailFastWhenExtensionLibraryHasNoInferableIdentityAndSameFileNameAlreadyExistsInCli() {
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
  void shouldFailFastWhenExtensionLibraryCoordinateMatchesCliWithDifferentVersion() {
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
  void shouldTreatExtensionLibraryWithoutNumericVersionSuffixAsMissingWithoutVersionConflict() {
    List<RuntimeLibraryEntry> extensionLibraries = List.of(RuntimeLibraryEntry.fromFileName("shared-lib-custom.jar"));
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(RuntimeLibraryEntry.fromFileName("shared-lib-1.0.0.jar"));

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).containsExactly("shared-lib-custom.jar");
  }
}
