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
class RuntimeExtensionModeEnablerTest {

  @Test
  void shouldUsePreparedModeChangePlanToEnableExtensionModeThroughInjectedRepository() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-enabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(runtimeJarPath.getParent());
    createFatJar(runtimeJarPath);
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: company-extension
        version: 1.0.0
      """
    );
    RuntimeModeConfigurationDocument currentConfiguration = new RuntimeModeConfigurationDocument(new LinkedHashMap<>());
    RecordingRuntimeModeConfigurationRepository runtimeModeConfigurationRepository = new RecordingRuntimeModeConfigurationRepository(
      configPath,
      currentConfiguration
    );
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = new Seed4JCliHome(userHome).runtimeExtensionConfiguration();
    RuntimeExtensionModeEnabler enabler = new RuntimeExtensionModeEnabler(
      runtimeExtensionConfiguration,
      runtimeModeConfigurationRepository
    );

    Path persistedConfigPath = enabler.enable();

    assertThat(persistedConfigPath).isEqualTo(configPath);
    assertThat(runtimeModeConfigurationRepository.prepareCalls()).isEqualTo(1);
    assertThat(runtimeModeConfigurationRepository.lastPreparedMode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeModeConfigurationRepository.applyCalls()).isEqualTo(1);
    assertThat(runtimeModeConfigurationRepository.lastPersistedConfiguration()).isEqualTo(currentConfiguration);
    assertThat(runtimeModeConfigurationRepository.lastPersistedMode()).isEqualTo(RuntimeMode.EXTENSION);
  }

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
    RuntimeExtensionModeEnabler enabler = enabler(userHome);

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
    RuntimeExtensionModeEnabler enabler = enabler(userHome);

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
    RuntimeExtensionModeEnabler enabler = enabler(userHome);

    assertThatThrownBy(enabler::enable)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not read ~/.config/seed4j-cli/config.yml.")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(YAMLException.class);
  }

  @Test
  void shouldPrioritizeConfigFailFastWhenConfigAndRuntimeArtifactsAreBothInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-enabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j: [broken");
    RuntimeExtensionModeEnabler enabler = enabler(userHome);

    assertThatThrownBy(enabler::enable)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not read ~/.config/seed4j-cli/config.yml.")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(YAMLException.class);
  }

  @Test
  void shouldFailWhenPersistingExtensionModeFails() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-enabler-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(runtimeJarPath.getParent());
    createFatJar(runtimeJarPath);
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: company-extension
        version: 1.0.0
      """
    );
    RuntimeModeConfigurationDocument currentConfiguration = new RuntimeModeConfigurationDocument(new LinkedHashMap<>());
    RecordingRuntimeModeConfigurationRepository runtimeModeConfigurationRepository = new RecordingRuntimeModeConfigurationRepository(
      configPath,
      currentConfiguration,
      new IOException("cannot persist")
    );
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = new Seed4JCliHome(userHome).runtimeExtensionConfiguration();
    RuntimeExtensionModeEnabler enabler = new RuntimeExtensionModeEnabler(
      runtimeExtensionConfiguration,
      runtimeModeConfigurationRepository
    );

    assertThatThrownBy(enabler::enable)
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

  private static RuntimeExtensionModeEnabler enabler(Path userHome) {
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = new Seed4JCliHome(userHome).runtimeExtensionConfiguration();

    return new RuntimeExtensionModeEnabler(
      runtimeExtensionConfiguration,
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome))
    );
  }

  private static final class RecordingRuntimeModeConfigurationRepository implements RuntimeModeConfigurationRepository {

    private final Path configPath;
    private final RuntimeModeConfigurationDocument currentConfiguration;
    private int prepareCalls;
    private int applyCalls;
    private RuntimeModeConfigurationDocument lastPersistedConfiguration;
    private RuntimeMode lastPersistedMode;
    private RuntimeMode lastPreparedMode;
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
    public RuntimeMode readMode() {
      return RuntimeMode.STANDARD;
    }

    @Override
    public RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
      prepareCalls = prepareCalls + 1;
      lastPreparedMode = targetMode;

      return new RuntimeModeChangePlan() {
        @Override
        public Path configPath() {
          return configPath;
        }

        @Override
        public void apply() throws IOException {
          if (persistFailure != null) {
            throw persistFailure;
          }

          applyCalls = applyCalls + 1;
          lastPersistedConfiguration = currentConfiguration;
          lastPersistedMode = targetMode;
        }
      };
    }

    private int prepareCalls() {
      return prepareCalls;
    }

    private int applyCalls() {
      return applyCalls;
    }

    private RuntimeModeConfigurationDocument lastPersistedConfiguration() {
      return lastPersistedConfiguration;
    }

    private RuntimeMode lastPersistedMode() {
      return lastPersistedMode;
    }

    private RuntimeMode lastPreparedMode() {
      return lastPreparedMode;
    }
  }
}
