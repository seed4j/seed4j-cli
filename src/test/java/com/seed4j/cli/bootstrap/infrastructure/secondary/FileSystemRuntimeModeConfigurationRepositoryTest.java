package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeChangePlan;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class FileSystemRuntimeModeConfigurationRepositoryTest {

  @Test
  void shouldPrepareModeChangeAndApplyExtensionModePreservingExistingConfigKeys() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-mode-configuration-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
        hidden-resources:
          slugs:
            - gradle-java
      custom:
        enabled: true
      """
    );
    FileSystemRuntimeModeConfigurationRepository repository = new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome));

    RuntimeModeChangePlan modeChangePlan = repository.prepareModeChange(RuntimeMode.EXTENSION);
    modeChangePlan.apply();

    String persistedConfiguration = Files.readString(configPath);
    assertThat(modeChangePlan.configPath()).isEqualTo(configPath);
    assertThat(persistedConfiguration)
      .contains("mode: extension")
      .contains("hidden-resources")
      .contains("gradle-java")
      .contains("custom:")
      .contains("enabled: true");
  }

  @Test
  void shouldFailDuringPrepareModeChangeWhenConfigurationIsInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-mode-configuration-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j: [broken");
    FileSystemRuntimeModeConfigurationRepository repository = new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome));

    assertThatThrownBy(() -> repository.prepareModeChange(RuntimeMode.EXTENSION))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not read ~/.config/seed4j-cli/config.yml.")
      .hasMessageContaining("Details:");

    assertThat(Files.readString(configPath)).isEqualTo("seed4j: [broken");
  }

  @Test
  void shouldFailDuringApplyWhenConfigurationCannotBeUpdated() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-mode-configuration-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
    FileSystemRuntimeModeConfigurationRepository repository = new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome));

    RuntimeModeChangePlan modeChangePlan = repository.prepareModeChange(RuntimeMode.EXTENSION);
    Files.delete(configPath);
    Files.createDirectory(configPath);

    assertThatThrownBy(modeChangePlan::apply)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not update ~/.config/seed4j-cli/config.yml.")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);
  }
}
