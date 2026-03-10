package com.seed4j.cli.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeSelectionTest {

  @Test
  void shouldDefaultToStandardModeWhenRuntimeConfigurationIsMissing() {
    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(null);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.STANDARD);
  }

  @Test
  void shouldIgnoreMissingExtensionArtifactsWhenModeIsStandard() {
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.STANDARD,
      new RuntimeExtensionConfiguration(Path.of("missing-extension.jar"), Path.of("missing-metadata.yml"))
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(runtimeSelection.extensionJarPath()).isEmpty();
  }

  @Test
  void shouldUseConfiguredJarPathWhenModeIsExtension() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path configuredJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.createFile(tempDirectory.resolve("extension-metadata.yml"));
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(configuredJarPath, metadataPath)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionJarPath()).contains(configuredJarPath);
  }

  @Test
  void shouldUseDefaultJarPathWhenModeIsExtensionAndConfiguredPathIsMissing() throws IOException {
    String originalUserHome = System.getProperty("user.home");
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");

    try {
      System.setProperty("user.home", tempDirectory.toString());
      Path metadataPath = Files.createFile(tempDirectory.resolve("extension-metadata.yml"));
      Path defaultJarPath = Path.of(tempDirectory.toString(), ".config", "seed4j-cli", "runtime", "active", "extension.jar");
      Files.createDirectories(defaultJarPath.getParent());
      Files.createFile(defaultJarPath);
      RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
        RuntimeMode.EXTENSION,
        RuntimeExtensionConfiguration.withDefaultJarPath(metadataPath)
      );

      RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration);

      assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
      assertThat(runtimeSelection.extensionJarPath()).contains(defaultJarPath);
    } finally {
      System.setProperty("user.home", originalUserHome);
    }
  }

  @Test
  void shouldFailWhenMetadataIsMissingInExtensionMode() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path missingMetadataPath = tempDirectory.resolve("missing-metadata.yml");
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, missingMetadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("metadata")
      .hasMessageContaining(missingMetadataPath.toString());
  }

  @Test
  void shouldFailWhenJarIsMissingInExtensionMode() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path missingJarPath = tempDirectory.resolve("missing-extension.jar");
    Path metadataPath = Files.createFile(tempDirectory.resolve("extension-metadata.yml"));
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(missingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("jar")
      .hasMessageContaining(missingJarPath.toString());
  }
}
