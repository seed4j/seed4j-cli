package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class RuntimeSelectionTest {

  private static final String CURRENT_CLI_VERSION = "0.0.1-SNAPSHOT";

  private static final String MINIMAL_EXTENSION_METADATA = """
    distribution:
      id: company-extension
      version: 1.0.0
    """;

  private static final String METADATA_WITH_MINIMUM_COMPATIBILITY = """
    distribution:
      id: company-extension
      version: 1.0.0
    compatibility:
      min-cli-version: 0.0.1
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
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), MINIMAL_EXTENSION_METADATA);
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
    Files.writeString(metadataPath, MINIMAL_EXTENSION_METADATA);
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
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), MINIMAL_EXTENSION_METADATA);
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
  void shouldAcceptWhenCompatibilitySectionIsMissing() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionJarPath()).contains(existingJarPath);
  }

  @Test
  void shouldAcceptWhenCompatibilitySectionIsEmpty() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
      compatibility: {}
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
  }

  @Test
  void shouldAcceptWhenCurrentCliVersionIsHigherThanMinimumCompatibility() throws IOException {
    String currentCliVersion = "0.0.2-SNAPSHOT";
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path configuredJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), METADATA_WITH_MINIMUM_COMPATIBILITY);
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
      compatibility:
        min-cli-version: 1.2.0
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
      compatibility:
        min-cli-version: 0.0.2
      """
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, currentCliVersion))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("compatibility.min-cli-version")
      .hasMessageContaining("0.0.2")
      .hasMessageContaining(currentCliVersion);
  }

  @ParameterizedTest
  @ValueSource(strings = { "1..2", "1.2.x", "not-a-version", "v1.2.3", "1,2,3" })
  void shouldFailWhenCompatibilityMinCliVersionHasInvalidFormat(String invalidMinCliVersion) throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(
      tempDirectory.resolve("extension-metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
      compatibility:
        min-cli-version: %s
      """.formatted(invalidMinCliVersion)
    );
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("compatibility.min-cli-version")
      .hasMessageContaining(invalidMinCliVersion);
  }

  @Test
  void shouldFailWhenCurrentCliVersionIsMalformedAndMinimumCompatibilityIsPresent() throws IOException {
    String currentCliVersion = "not-a-version";
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), METADATA_WITH_MINIMUM_COMPATIBILITY);
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
  void shouldAcceptWhenCurrentCliVersionIsMalformedAndMinimumCompatibilityIsMissing() throws IOException {
    String currentCliVersion = "not-a-version";
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, currentCliVersion);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionJarPath()).contains(existingJarPath);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidDistributionAndCompatibilityMetadata")
  void shouldFailWhenDistributionOrCompatibilityMetadataIsInvalid(
    String scenarioName,
    String metadataContent,
    String expectedMessageFragment
  ) throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path existingJarPath = Files.createFile(tempDirectory.resolve("company-extension.jar"));
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), metadataContent);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(existingJarPath, metadataPath)
    );

    assertThatThrownBy(() -> RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining(expectedMessageFragment);
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
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), MINIMAL_EXTENSION_METADATA);
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
    Path metadataPath = Files.writeString(tempDirectory.resolve("extension-metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(configuredJarPath, metadataPath)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, CURRENT_CLI_VERSION);

    assertThat(runtimeSelection.distributionVersion()).contains("1.0.0");
  }

  private static Stream<Arguments> invalidDistributionAndCompatibilityMetadata() {
    return Stream.of(
      Arguments.of(
        "distribution.id missing",
        """
        distribution:
          version: 1.0.0
        """,
        "distribution.id"
      ),
      Arguments.of(
        "distribution.id blank",
        """
        distribution:
          id: "   "
          version: 1.0.0
        """,
        "distribution.id"
      ),
      Arguments.of(
        "distribution.version missing",
        """
        distribution:
          id: company-extension
        """,
        "distribution.version"
      ),
      Arguments.of(
        "distribution is not a map",
        """
        distribution: invalid
        """,
        "distribution"
      ),
      Arguments.of(
        "compatibility is not a map",
        """
        distribution:
          id: company-extension
          version: 1.0.0
        compatibility: invalid
        """,
        "compatibility"
      ),
      Arguments.of(
        "compatibility.min-cli-version blank",
        """
        distribution:
          id: company-extension
          version: 1.0.0
        compatibility:
          min-cli-version: "   "
        """,
        "compatibility.min-cli-version"
      ),
      Arguments.of(
        "compatibility.min-cli-version is not a string",
        """
        distribution:
          id: company-extension
          version: 1.0.0
        compatibility:
          min-cli-version: 123
        """,
        "compatibility.min-cli-version"
      )
    );
  }
}
