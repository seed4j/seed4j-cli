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
class RuntimeExtensionInstallerTest {

  private static final String DISTRIBUTION_ID = "company-extension";
  private static final String DISTRIBUTION_VERSION = "1.0.0";

  @Test
  void shouldCreateConfigFileWhenMissingAndInstallExtensionRuntime() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    RuntimeExtensionInstaller installer = new RuntimeExtensionInstaller(userHome);
    RuntimeExtensionInstallRequest request = new RuntimeExtensionInstallRequest(extensionJarPath, DISTRIBUTION_ID, DISTRIBUTION_VERSION);

    RuntimeExtensionInstallResult installResult = installer.install(request);

    assertThat(configPath).exists();
    assertThat(Files.readString(configPath)).contains("mode: extension");
    assertThat(runtimeJarPath).exists();
    assertThat(metadataPath).exists();
    assertThat(installResult.extensionJarPath()).isEqualTo(runtimeJarPath);
    assertThat(installResult.metadataPath()).isEqualTo(metadataPath);
    assertThat(installResult.configPath()).isEqualTo(configPath);
    assertThat(installResult.runtimeReplaced()).isFalse();
  }

  @Test
  void shouldFailWhenConfigFileExistsButIsInvalidWithoutMutatingRuntimeArtifacts() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j: [broken");
    RuntimeExtensionInstaller installer = new RuntimeExtensionInstaller(userHome);
    RuntimeExtensionInstallRequest request = new RuntimeExtensionInstallRequest(extensionJarPath, DISTRIBUTION_ID, DISTRIBUTION_VERSION);

    assertThatThrownBy(() -> installer.install(request))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Could not read ~/.config/seed4j-cli/config.yml.");

    assertThat(runtimeJarPath).doesNotExist();
    assertThat(metadataPath).doesNotExist();
    assertThat(Files.readString(configPath)).isEqualTo("seed4j: [broken");
  }

  @Test
  void shouldPreserveExistingConfigKeysWhenConfigIsValid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
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
    RuntimeExtensionInstaller installer = new RuntimeExtensionInstaller(userHome);
    RuntimeExtensionInstallRequest request = new RuntimeExtensionInstallRequest(extensionJarPath, DISTRIBUTION_ID, DISTRIBUTION_VERSION);

    installer.install(request);

    String persistedConfiguration = Files.readString(configPath);
    assertThat(persistedConfiguration).contains("mode: extension");
    assertThat(persistedConfiguration).contains("hidden-resources");
    assertThat(persistedConfiguration).contains("gradle-java");
    assertThat(persistedConfiguration).contains("custom:");
    assertThat(persistedConfiguration).contains("enabled: true");
  }

  @Test
  void shouldOverwriteRuntimeArtifactsWhenActiveExtensionAlreadyExists() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(runtimeJarPath.getParent());
    createFatJar(runtimeJarPath, "BOOT-INF/classes/com/company/Legacy.class", new byte[] { 1 });
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: legacy-extension
        version: 0.9.0
      """
    );
    Path extensionJarPath = createFatJar(
      userHome.resolve("company-extension.jar"),
      "BOOT-INF/classes/com/company/New.class",
      new byte[] { 2, 3 }
    );
    RuntimeExtensionInstaller installer = new RuntimeExtensionInstaller(userHome);
    RuntimeExtensionInstallRequest request = new RuntimeExtensionInstallRequest(extensionJarPath, DISTRIBUTION_ID, DISTRIBUTION_VERSION);

    RuntimeExtensionInstallResult installResult = installer.install(request);

    assertThat(installResult.runtimeReplaced()).isTrue();
    assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(Files.readString(metadataPath)).contains("id: " + DISTRIBUTION_ID);
    assertThat(Files.readString(metadataPath)).contains("version: " + DISTRIBUTION_VERSION);
  }

  @Test
  void shouldFailWhenJarDoesNotContainBootInfClasses() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFlatJar(userHome.resolve("company-extension.jar"));
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    RuntimeExtensionInstaller installer = new RuntimeExtensionInstaller(userHome);
    RuntimeExtensionInstallRequest request = new RuntimeExtensionInstallRequest(extensionJarPath, DISTRIBUTION_ID, DISTRIBUTION_VERSION);

    assertThatThrownBy(() -> installer.install(request))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Invalid runtime jar file")
      .hasMessageContaining("BOOT-INF/classes");

    assertThat(runtimeJarPath).doesNotExist();
    assertThat(metadataPath).doesNotExist();
    assertThat(configPath).doesNotExist();
  }

  private static Path createFatJar(Path jarPath) throws IOException {
    return createFatJar(jarPath, "BOOT-INF/classes/", new byte[] {});
  }

  private static Path createFatJar(Path jarPath, String additionalEntryName, byte[] additionalEntryContent) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
      if (!"BOOT-INF/classes/".equals(additionalEntryName)) {
        jarOutputStream.putNextEntry(new JarEntry(additionalEntryName));
        jarOutputStream.write(additionalEntryContent);
        jarOutputStream.closeEntry();
      }
    }

    return jarPath;
  }

  private static Path createFlatJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("com/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("com/company/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("com/company/Extension.class"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
    }

    return jarPath;
  }
}
