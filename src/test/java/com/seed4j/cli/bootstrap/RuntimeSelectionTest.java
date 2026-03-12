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

  private static final String CURRENT_CLI_VERSION = "0.0.1-SNAPSHOT";

  private static final String VALID_EXTENSION_METADATA = """
    distribution:
      id: company-extension
      version: 1.0.0
      kind: extension
      vendor: acme
    artifact:
      filename: company-extension.jar
    compatibility:
      cli: 0.0.1
    """;
  private static final String VALID_DEFAULT_EXTENSION_METADATA = """
    distribution:
      id: company-extension
      version: 1.0.0
      kind: extension
      vendor: acme
    artifact:
      filename: extension.jar
    compatibility:
      cli: 0.0.1
    """;

  @Test
  void shouldDefaultToStandardModeWhenRuntimeConfigurationIsMissing() {
    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(null, CURRENT_CLI_VERSION);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.STANDARD);
  }

  @Test
  void shouldIgnoreMissingExtensionArtifactsWhenModeIsStandard() {
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.STANDARD,
      new RuntimeExtensionConfiguration(Path.of("missing-extension.jar"), Path.of("missing-metadata.yml"))
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(runtimeSelection.extensionJarPath()).isEmpty();
  }

  @Test
  void shouldUseConfiguredJarPathWhenModeIsExtension() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path configuredJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), VALID_EXTENSION_METADATA);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(configuredJarPath, metadataPath)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionJarPath()).contains(configuredJarPath);
  }

  @Test
  void shouldUseDefaultJarPathWhenModeIsExtensionAndConfiguredPathIsMissing() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path defaultJarPath = tempDirectory.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = tempDirectory.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(defaultJarPath.getParent());
    Files.createFile(defaultJarPath);
    Files.writeString(metadataPath, VALID_DEFAULT_EXTENSION_METADATA);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      RuntimeExtensionConfiguration.withDefaultPaths(tempDirectory)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionJarPath()).contains(defaultJarPath);
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

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
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

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("jar")
      .hasMessageContaining(missingJarPath.toString());
  }

  @Test
  void shouldFailWhenDistributionKindIsNotExtension() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
        kind: standard
        vendor: acme
      artifact:
        filename: company-extension.jar
      compatibility:
        cli: 0.0.1
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("distribution.kind")
      .hasMessageContaining("extension");
  }

  @Test
  void shouldFailWhenArtifactFilenameDoesNotMatchSelectedJar() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
        kind: extension
        vendor: acme
      artifact:
        filename: other-extension.jar
      compatibility:
        cli: 0.0.1
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("artifact.filename")
      .hasMessageContaining("other-extension.jar")
      .hasMessageContaining("company-extension.jar");
  }

  @Test
  void shouldFailWhenCliCompatibilityIsMissing() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
        kind: extension
        vendor: acme
      artifact:
        filename: company-extension.jar
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("compatibility.cli");
  }

  @Test
  void shouldFailWhenCliCompatibilityIsIncompatible() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
        kind: extension
        vendor: acme
      artifact:
        filename: company-extension.jar
      compatibility:
        cli: 999.0.0
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("compatibility.cli")
      .hasMessageContaining("999.0.0")
      .hasMessageContaining(CURRENT_CLI_VERSION);
  }

  @Test
  void shouldAcceptWhenCurrentCliVersionIsHigherThanMinimumCompatibility() throws IOException {
    String currentCliVersion = "0.0.2-SNAPSHOT";
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path configuredJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), VALID_EXTENSION_METADATA);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(configuredJarPath, metadataPath)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, currentCliVersion);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionJarPath()).contains(configuredJarPath);
  }

  @Test
  void shouldAcceptWhenCurrentCliVersionDiffersOnlyByTrailingZeroSegment() throws IOException {
    String currentCliVersion = "1.2";
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path configuredJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
        kind: extension
        vendor: acme
      artifact:
        filename: company-extension.jar
      compatibility:
        cli: 1.2.0
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(configuredJarPath, metadataPath)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, currentCliVersion);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionJarPath()).contains(configuredJarPath);
  }

  @Test
  void shouldFailWhenCurrentCliVersionIsLowerThanMinimumCompatibility() throws IOException {
    String currentCliVersion = "0.0.1-SNAPSHOT";
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
        kind: extension
        vendor: acme
      artifact:
        filename: company-extension.jar
      compatibility:
        cli: 0.0.2
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, currentCliVersion))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("compatibility.cli")
      .hasMessageContaining("0.0.2")
      .hasMessageContaining(currentCliVersion);
  }

  @Test
  void shouldFailWhenCompatibilityCliVersionIsMalformed() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
        kind: extension
        vendor: acme
      artifact:
        filename: company-extension.jar
      compatibility:
        cli: not-a-version
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("compatibility.cli")
      .hasMessageContaining("not-a-version");
  }

  @Test
  void shouldFailWhenCurrentCliVersionIsMalformed() throws IOException {
    String currentCliVersion = "not-a-version";
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), VALID_EXTENSION_METADATA);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, currentCliVersion))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("current CLI version")
      .hasMessageContaining(currentCliVersion);
  }

  @Test
  void shouldFailWhenDistributionIdIsMissing() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        version: 1.0.0
        kind: extension
        vendor: acme
      artifact:
        filename: company-extension.jar
      compatibility:
        cli: 0.0.1
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("distribution.id");
  }

  @Test
  void shouldFailWhenDistributionIdIsBlank() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: "   "
        version: 1.0.0
        kind: extension
        vendor: acme
      artifact:
        filename: company-extension.jar
      compatibility:
        cli: 0.0.1
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("distribution.id");
  }

  @Test
  void shouldFailWhenDistributionVersionIsMissing() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: company-extension
        kind: extension
        vendor: acme
      artifact:
        filename: company-extension.jar
      compatibility:
        cli: 0.0.1
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("distribution.version");
  }

  @Test
  void shouldFailWhenDistributionVendorIsMissing() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
        kind: extension
      artifact:
        filename: company-extension.jar
      compatibility:
        cli: 0.0.1
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("distribution.vendor");
  }

  @Test
  void shouldFailWhenMetadataRootIsNotAMap() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), "invalid-root");
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Invalid runtime metadata file")
      .hasMessageContaining(metadataPath.toString());
  }

  @Test
  void shouldFailWhenMetadataCannotBeParsed() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), "distribution: [broken");
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Invalid runtime metadata file")
      .hasMessageContaining(metadataPath.toString());
  }

  @Test
  void shouldExposeDistributionIdWhenExtensionRuntimeIsSelected() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path configuredJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), VALID_EXTENSION_METADATA);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(configuredJarPath, metadataPath)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION);

    assertThat(runtimeSelection.distributionId()).contains("company-extension");
  }

  @Test
  void shouldExposeDistributionVersionWhenExtensionRuntimeIsSelected() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path configuredJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), VALID_EXTENSION_METADATA);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(configuredJarPath, metadataPath)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION);

    assertThat(runtimeSelection.distributionVersion()).contains("1.0.0");
  }
}
