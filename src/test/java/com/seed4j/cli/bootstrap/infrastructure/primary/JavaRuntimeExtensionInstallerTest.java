package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.RuntimeExtensionApplicationService;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionInstaller.JavaRuntimeExtensionInstallation;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionInstaller.JavaRuntimeExtensionInstallationException;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionInstaller.JavaRuntimeExtensionInstallationRequest;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionArtifactsRepository;
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
class JavaRuntimeExtensionInstallerTest {

  private static final String DISTRIBUTION_ID = "company-extension";
  private static final String DISTRIBUTION_VERSION = "1.0.0";

  @Test
  void shouldCreateConfigFileAndInstallExtensionRuntime() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-java-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path configPath = configPath(userHome);
    Path runtimeJarPath = runtimeJarPath(userHome);
    Path metadataPath = metadataPath(userHome);
    JavaRuntimeExtensionInstaller installer = installer(userHome);

    JavaRuntimeExtensionInstallation installation = installer.install(installRequest(extensionJarPath));

    assertThat(configPath).exists();
    assertThat(Files.readString(configPath)).contains("mode: extension");
    assertThat(runtimeJarPath).exists();
    assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(Files.readString(metadataPath)).contains("id: " + DISTRIBUTION_ID).contains("version: " + DISTRIBUTION_VERSION);
    assertThat(installation.extensionJarPath()).isEqualTo(runtimeJarPath);
    assertThat(installation.metadataPath()).isEqualTo(metadataPath);
    assertThat(installation.configPath()).isEqualTo(configPath);
    assertThat(installation.runtimeReplaced()).isFalse();
  }

  @Test
  void shouldPreserveExistingConfigKeysWhenChangingRuntimeModeToExtension() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-java-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path configPath = configPath(userHome);
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
    JavaRuntimeExtensionInstaller installer = installer(userHome);

    installer.install(installRequest(extensionJarPath));

    assertThat(Files.readString(configPath))
      .contains("mode: extension")
      .contains("hidden-resources")
      .contains("gradle-java")
      .contains("custom:")
      .contains("enabled: true");
  }

  @Test
  void shouldReplaceExistingActiveExtensionRuntime() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-java-runtime-installer-");
    Path runtimeJarPath = runtimeJarPath(userHome);
    Path metadataPath = metadataPath(userHome);
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
    JavaRuntimeExtensionInstaller installer = installer(userHome);

    JavaRuntimeExtensionInstallation installation = installer.install(installRequest(extensionJarPath));

    assertThat(installation.runtimeReplaced()).isTrue();
    assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(Files.readString(metadataPath)).contains("id: " + DISTRIBUTION_ID).contains("version: " + DISTRIBUTION_VERSION);
  }

  @Test
  void shouldFailWithInvalidConfigWithoutPublishingRuntimeArtifacts() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-java-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path configPath = configPath(userHome);
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j: [broken");
    JavaRuntimeExtensionInstaller installer = installer(userHome);

    assertThatThrownBy(() -> installer.install(installRequest(extensionJarPath)))
      .isExactlyInstanceOf(JavaRuntimeExtensionInstallationException.class)
      .hasMessageContaining("Could not read ~/.config/seed4j-cli/config.yml.")
      .hasMessageContaining("Details:")
      .hasRootCauseInstanceOf(YAMLException.class);

    assertThat(runtimeJarPath(userHome)).doesNotExist();
    assertThat(metadataPath(userHome)).doesNotExist();
    assertThat(Files.readString(configPath)).isEqualTo("seed4j: [broken");
  }

  @Test
  void shouldRejectJarWithoutBootInfClassesBeforePublishingRuntimeArtifactsOrConfig() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-java-runtime-installer-");
    Path extensionJarPath = createFlatJar(userHome.resolve("company-extension.jar"));
    JavaRuntimeExtensionInstaller installer = installer(userHome);

    assertThatThrownBy(() -> installer.install(installRequest(extensionJarPath)))
      .isExactlyInstanceOf(JavaRuntimeExtensionInstallationException.class)
      .hasMessageContaining("Invalid runtime jar file")
      .hasMessageContaining("BOOT-INF/classes");

    assertThat(runtimeJarPath(userHome)).doesNotExist();
    assertThat(metadataPath(userHome)).doesNotExist();
    assertThat(configPath(userHome)).doesNotExist();
  }

  private static JavaRuntimeExtensionInstallationRequest installRequest(Path extensionJarPath) {
    return new JavaRuntimeExtensionInstallationRequest(
      extensionJarPath.toString(),
      new RuntimeDistributionId(DISTRIBUTION_ID).id(),
      new RuntimeDistributionVersion(DISTRIBUTION_VERSION).version()
    );
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

  private static JavaRuntimeExtensionInstaller installer(Path userHome) {
    Seed4JCliHome cliHome = new Seed4JCliHome(userHome);
    RuntimeExtensionApplicationService applicationService = new RuntimeExtensionApplicationService(
      new JarRuntimeExtensionPackageValidator(),
      new FileSystemRuntimeModeConfigurationRepository(cliHome),
      new FileSystemRuntimeExtensionArtifactsRepository(cliHome),
      RuntimeSelection.standard()
    );

    return new JavaRuntimeExtensionInstaller(applicationService);
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
