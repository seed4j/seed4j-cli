package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.RuntimeExtensionApplicationService;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionModeSwitcher.JavaRuntimeExtensionModeSwitch;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionModeSwitcher.JavaRuntimeExtensionModeSwitchException;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionSelectionRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JarRuntimeExtensionPackageValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.error.YAMLException;

@UnitTest
class JavaRuntimeExtensionModeSwitcherTest {

  @Test
  void shouldEnableExtensionModeWhenActiveRuntimeArtifactsAndConfigAreValid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-java-runtime-mode-switcher-");
    Path configPath = configPath(userHome);
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, standardModeConfig());
    publishActiveRuntime(userHome);
    JavaRuntimeExtensionModeSwitcher switcher = switcher(userHome);

    JavaRuntimeExtensionModeSwitch modeSwitch = switcher.enable();

    assertThat(modeSwitch.configPath()).isEqualTo(configPath);
    assertThat(Files.readString(configPath)).contains("mode: extension");
  }

  @Test
  void shouldFailWhenActiveRuntimeArtifactsAreInvalidAndPreserveConfig() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-java-runtime-mode-switcher-");
    Path configPath = configPath(userHome);
    Path runtimeJarPath = runtimeJarPath(userHome);
    Files.createDirectories(configPath.getParent());
    Files.createDirectories(runtimeJarPath.getParent());
    Files.writeString(configPath, standardModeConfig());
    createFatJar(runtimeJarPath);
    JavaRuntimeExtensionModeSwitcher switcher = switcher(userHome);

    assertThatThrownBy(switcher::enable)
      .isExactlyInstanceOf(JavaRuntimeExtensionModeSwitchException.class)
      .hasMessageContaining("Invalid runtime metadata file")
      .hasCauseInstanceOf(com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException.class);

    assertThat(Files.readString(configPath)).isEqualTo(standardModeConfig());
  }

  @Test
  void shouldFailWithInvalidConfigBeforeValidatingActiveRuntimeArtifactsAndPreserveConfig() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-java-runtime-mode-switcher-");
    Path configPath = configPath(userHome);
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j: [broken");
    JavaRuntimeExtensionModeSwitcher switcher = switcher(userHome);

    assertThatThrownBy(switcher::enable)
      .isExactlyInstanceOf(JavaRuntimeExtensionModeSwitchException.class)
      .hasMessageContaining("Could not read ~/.config/seed4j-cli/config.yml.")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException.class)
      .hasRootCauseInstanceOf(YAMLException.class);

    assertThat(Files.readString(configPath)).isEqualTo("seed4j: [broken");
  }

  private static void publishActiveRuntime(Path userHome) throws IOException {
    Path runtimeJarPath = runtimeJarPath(userHome);
    Path metadataPath = metadataPath(userHome);
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

  private static JavaRuntimeExtensionModeSwitcher switcher(Path userHome) {
    Seed4JCliHome cliHome = new Seed4JCliHome(userHome);
    RuntimeExtensionApplicationService applicationService = new RuntimeExtensionApplicationService(
      new JarRuntimeExtensionPackageValidator(),
      new FileSystemRuntimeModeConfigurationRepository(cliHome),
      new FileSystemRuntimeExtensionArtifactsRepository(cliHome),
      new FileSystemRuntimeExtensionSelectionRepository(cliHome, new JarRuntimeExtensionPackageValidator()),
      RuntimeSelection.standard()
    );

    return new JavaRuntimeExtensionModeSwitcher(applicationService);
  }

  private static String standardModeConfig() {
    return """
    seed4j:
      runtime:
        mode: standard
    """;
  }

  private static Path configPath(Path userHome) {
    return userHome.resolve(".config/seed4j-cli/config.yml");
  }

  private static Path runtimeJarPath(Path userHome) {
    return userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
  }

  private static Path metadataPath(Path userHome) {
    return userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
  }
}
