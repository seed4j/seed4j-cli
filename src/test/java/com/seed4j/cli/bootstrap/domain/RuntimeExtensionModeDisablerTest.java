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
class RuntimeExtensionModeDisablerTest {

  @Test
  void shouldWriteStandardModeWhenConfigFileIsMissing() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-disabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    RuntimeExtensionModeDisabler disabler = new RuntimeExtensionModeDisabler(userHome);

    Path persistedConfigPath = disabler.disable();

    assertThat(persistedConfigPath).isEqualTo(configPath);
    assertThat(configPath).exists();
    assertThat(Files.readString(configPath)).contains("mode: standard");
  }

  @Test
  void shouldPreserveOtherValidConfigKeysWhileSwitchingToStandardMode() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-disabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: extension
        hidden-resources:
          slugs:
            - gradle-java
      custom:
        enabled: true
      """
    );
    RuntimeExtensionModeDisabler disabler = new RuntimeExtensionModeDisabler(userHome);

    disabler.disable();

    String persistedConfig = Files.readString(configPath);
    assertThat(persistedConfig)
      .contains("mode: standard")
      .contains("hidden-resources")
      .contains("gradle-java")
      .contains("custom:")
      .contains("enabled: true");
  }

  @Test
  void shouldNotDeleteRuntimeArtifactsWhenDisablingExtensionMode() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-disabler-");
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
          mode: extension
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
    byte[] runtimeJarContentBeforeDisable = Files.readAllBytes(runtimeJarPath);
    String metadataContentBeforeDisable = Files.readString(metadataPath);
    RuntimeExtensionModeDisabler disabler = new RuntimeExtensionModeDisabler(userHome);

    disabler.disable();

    assertThat(runtimeJarPath).exists();
    assertThat(metadataPath).exists();
    assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(runtimeJarContentBeforeDisable);
    assertThat(Files.readString(metadataPath)).isEqualTo(metadataContentBeforeDisable);
  }

  @Test
  void shouldFailFastWhenConfigFileIsInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-disabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j: [broken");
    RuntimeExtensionModeDisabler disabler = new RuntimeExtensionModeDisabler(userHome);

    assertThatThrownBy(disabler::disable)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Could not read ~/.config/seed4j-cli/config.yml.");

    assertThat(Files.readString(configPath)).isEqualTo("seed4j: [broken");
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
