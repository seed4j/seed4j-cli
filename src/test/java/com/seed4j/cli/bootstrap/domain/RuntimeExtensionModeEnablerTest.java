package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionModeEnablerTest {

  @Test
  void shouldEnableExtensionModeWhenRuntimeArtifactsAndConfigAreValid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-enabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(configPath.getParent());
    Files.createDirectories(runtimeJarPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
    createFatJar(runtimeJarPath);
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: company-extension
        version: 1.0.0
      """
    );
    RuntimeExtensionModeEnabler enabler = new RuntimeExtensionModeEnabler(userHome);

    Path persistedConfigPath = enabler.enable();

    assertThat(persistedConfigPath).isEqualTo(configPath);
    assertThat(Files.readString(configPath)).contains("mode: extension");
  }

  @Test
  void shouldFailWhenRuntimeArtifactsAreInvalidAndNotMutateConfigFile() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-enabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Files.createDirectories(configPath.getParent());
    Files.createDirectories(runtimeJarPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
    createFatJar(runtimeJarPath);
    RuntimeExtensionModeEnabler enabler = new RuntimeExtensionModeEnabler(userHome);

    assertThatThrownBy(enabler::enable)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Invalid runtime metadata file");

    assertThat(Files.readString(configPath)).isEqualTo(
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
  }

  @Test
  void shouldFailFastWhenConfigFileIsInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-enabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(configPath.getParent());
    Files.createDirectories(runtimeJarPath.getParent());
    Files.writeString(configPath, "seed4j: [broken");
    createFatJar(runtimeJarPath);
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: company-extension
        version: 1.0.0
      """
    );
    RuntimeExtensionModeEnabler enabler = new RuntimeExtensionModeEnabler(userHome);

    assertThatThrownBy(enabler::enable)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Could not read ~/.config/seed4j-cli/config.yml.");
  }

  @Test
  void shouldPrioritizeConfigFailFastWhenConfigAndRuntimeArtifactsAreBothInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-enabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j: [broken");
    RuntimeExtensionModeEnabler enabler = new RuntimeExtensionModeEnabler(userHome);

    assertThatThrownBy(enabler::enable)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Could not read ~/.config/seed4j-cli/config.yml.");
  }

  private static Path createFatJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
    }

    return jarPath;
  }
}
