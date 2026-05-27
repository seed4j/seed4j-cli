package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.error.YAMLException;

@UnitTest
class RuntimeExtensionModeDisablerTest {

  @Test
  void shouldPersistStandardModeThroughInjectedRuntimeModeConfigurationRepository() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-disabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    RuntimeModeConfigurationDocument currentConfiguration = new RuntimeModeConfigurationDocument(new LinkedHashMap<>());
    RecordingRuntimeModeConfigurationRepository runtimeModeConfigurationRepository = new RecordingRuntimeModeConfigurationRepository(
      configPath,
      currentConfiguration
    );
    RuntimeExtensionModeDisabler disabler = new RuntimeExtensionModeDisabler(runtimeModeConfigurationRepository);

    Path persistedConfigPath = disabler.disable();

    assertThat(persistedConfigPath).isEqualTo(configPath);
    assertThat(runtimeModeConfigurationRepository.readCalls()).isEqualTo(1);
    assertThat(runtimeModeConfigurationRepository.persistCalls()).isEqualTo(1);
    assertThat(runtimeModeConfigurationRepository.lastPersistedConfiguration()).isEqualTo(currentConfiguration);
    assertThat(runtimeModeConfigurationRepository.lastPersistedMode()).isEqualTo(RuntimeMode.STANDARD);
  }

  @Test
  void shouldWriteStandardModeWhenConfigFileIsMissing() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-disabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    RuntimeExtensionModeDisabler disabler = disabler(userHome);

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
    RuntimeExtensionModeDisabler disabler = disabler(userHome);

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
    RuntimeExtensionModeDisabler disabler = disabler(userHome);

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
    RuntimeExtensionModeDisabler disabler = disabler(userHome);

    assertThatThrownBy(disabler::disable)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not read ~/.config/seed4j-cli/config.yml.")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(YAMLException.class);
    assertThat(Files.readString(configPath)).isEqualTo("seed4j: [broken");
  }

  @Test
  void shouldFailWhenPersistingStandardModeFails() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-disabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    RuntimeModeConfigurationDocument currentConfiguration = new RuntimeModeConfigurationDocument(new LinkedHashMap<>());
    RecordingRuntimeModeConfigurationRepository runtimeModeConfigurationRepository = new RecordingRuntimeModeConfigurationRepository(
      configPath,
      currentConfiguration,
      new IOException("cannot persist")
    );
    RuntimeExtensionModeDisabler disabler = new RuntimeExtensionModeDisabler(runtimeModeConfigurationRepository);

    assertThatThrownBy(disabler::disable)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not update ~/.config/seed4j-cli/config.yml.")
      .hasMessageContaining("Details: cannot persist")
      .hasCauseInstanceOf(IOException.class);
  }

  private static void createFatJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
    }
  }

  private static RuntimeExtensionModeDisabler disabler(Path userHome) {
    return new RuntimeExtensionModeDisabler(new FileSystemRuntimeModeConfigurationRepository(userHome));
  }

  private static final class RecordingRuntimeModeConfigurationRepository implements RuntimeModeConfigurationRepository {

    private final Path configPath;
    private final RuntimeModeConfigurationDocument currentConfiguration;
    private int readCalls;
    private int persistCalls;
    private RuntimeModeConfigurationDocument lastPersistedConfiguration;
    private RuntimeMode lastPersistedMode;
    private final IOException persistFailure;

    private RecordingRuntimeModeConfigurationRepository(Path configPath, RuntimeModeConfigurationDocument currentConfiguration) {
      this(configPath, currentConfiguration, null);
    }

    private RecordingRuntimeModeConfigurationRepository(
      Path configPath,
      RuntimeModeConfigurationDocument currentConfiguration,
      IOException persistFailure
    ) {
      this.configPath = configPath;
      this.currentConfiguration = currentConfiguration;
      this.persistFailure = persistFailure;
    }

    @Override
    public Path configPath() {
      return configPath;
    }

    @Override
    public RuntimeModeConfigurationDocument readConfiguration() {
      readCalls = readCalls + 1;
      return currentConfiguration;
    }

    @Override
    public RuntimeMode readMode() {
      return RuntimeMode.STANDARD;
    }

    @Override
    public void persistMode(RuntimeModeConfigurationDocument currentConfiguration, RuntimeMode mode) throws IOException {
      if (persistFailure != null) {
        throw persistFailure;
      }

      persistCalls = persistCalls + 1;
      lastPersistedConfiguration = currentConfiguration;
      lastPersistedMode = mode;
    }

    private int readCalls() {
      return readCalls;
    }

    private int persistCalls() {
      return persistCalls;
    }

    private RuntimeModeConfigurationDocument lastPersistedConfiguration() {
      return lastPersistedConfiguration;
    }

    private RuntimeMode lastPersistedMode() {
      return lastPersistedMode;
    }
  }
}
