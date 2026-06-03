package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
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
class FileSystemRuntimeExtensionSelectionRepositoryTest {

  @Test
  void shouldResolveActiveExtensionRuntimeSelection() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path extensionJarPath = activeExtensionJarPath(userHome);
    Path metadataPath = activeMetadataPath(userHome);
    Files.createDirectories(extensionJarPath.getParent());
    createFatJar(extensionJarPath);
    Files.writeString(metadataPath, metadata("company-extension", "1.0.0"));
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    RuntimeSelection runtimeSelection = repository.activeRuntimeSelection();

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionJarPath()).contains(
      com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath.from(extensionJarPath.toString())
    );
    assertThat(runtimeSelection.distributionId()).contains(new RuntimeDistributionId("company-extension"));
    assertThat(runtimeSelection.distributionVersion()).contains(new RuntimeDistributionVersion("1.0.0"));
  }

  @Test
  void shouldFailWhenActiveMetadataIsMissing() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path extensionJarPath = activeExtensionJarPath(userHome);
    Files.createDirectories(extensionJarPath.getParent());
    createFatJar(extensionJarPath);
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    assertThatThrownBy(repository::activeRuntimeSelection)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Invalid runtime metadata file: " + activeMetadataPath(userHome));
  }

  @Test
  void shouldFailWhenActiveJarIsMissing() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path metadataPath = activeMetadataPath(userHome);
    Files.createDirectories(metadataPath.getParent());
    Files.writeString(metadataPath, metadata("company-extension", "1.0.0"));
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    assertThatThrownBy(repository::activeRuntimeSelection)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Invalid runtime jar file: " + activeExtensionJarPath(userHome));
  }

  @Test
  void shouldFailWhenActiveJarDoesNotContainBootInfClasses() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path extensionJarPath = activeExtensionJarPath(userHome);
    Path metadataPath = activeMetadataPath(userHome);
    Files.createDirectories(extensionJarPath.getParent());
    createFlatJar(extensionJarPath);
    Files.writeString(metadataPath, metadata("company-extension", "1.0.0"));
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    assertThatThrownBy(repository::activeRuntimeSelection)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Invalid runtime jar file: " + extensionJarPath)
      .hasMessageContaining("BOOT-INF/classes");
  }

  @Test
  void shouldFailWhenActiveJarIsNotAValidJarFile() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path extensionJarPath = activeExtensionJarPath(userHome);
    Path metadataPath = activeMetadataPath(userHome);
    Files.createDirectories(extensionJarPath.getParent());
    Files.writeString(extensionJarPath, "not a jar");
    Files.writeString(metadataPath, metadata("company-extension", "1.0.0"));
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    assertThatThrownBy(repository::activeRuntimeSelection)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Invalid runtime jar file: " + extensionJarPath)
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldAcceptActiveJarWithBootInfClassesEntryWithoutTrailingSlash() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path extensionJarPath = activeExtensionJarPath(userHome);
    Path metadataPath = activeMetadataPath(userHome);
    Files.createDirectories(extensionJarPath.getParent());
    createJar(extensionJarPath, "BOOT-INF/classes");
    Files.writeString(metadataPath, metadata("company-extension", "1.0.0"));
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    RuntimeSelection runtimeSelection = repository.activeRuntimeSelection();

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionJarPath()).contains(
      com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath.from(extensionJarPath.toString())
    );
  }

  @Test
  void shouldAcceptActiveJarWithBootInfClassesChildEntry() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path extensionJarPath = activeExtensionJarPath(userHome);
    Path metadataPath = activeMetadataPath(userHome);
    Files.createDirectories(extensionJarPath.getParent());
    createJar(extensionJarPath, "BOOT-INF/classes/com/company/Extension.class");
    Files.writeString(metadataPath, metadata("company-extension", "1.0.0"));
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    RuntimeSelection runtimeSelection = repository.activeRuntimeSelection();

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.distributionId()).contains(new RuntimeDistributionId("company-extension"));
    assertThat(runtimeSelection.distributionVersion()).contains(new RuntimeDistributionVersion("1.0.0"));
  }

  @Test
  void shouldFailWhenMetadataRootIsNotAMap() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path extensionJarPath = activeExtensionJarPath(userHome);
    Path metadataPath = activeMetadataPath(userHome);
    Files.createDirectories(extensionJarPath.getParent());
    createFatJar(extensionJarPath);
    Files.writeString(metadataPath, "- company-extension");
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    assertThatThrownBy(repository::activeRuntimeSelection)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Invalid runtime metadata file: " + metadataPath);
  }

  @Test
  void shouldFailWhenMetadataDistributionIsNotAMap() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path extensionJarPath = activeExtensionJarPath(userHome);
    Path metadataPath = activeMetadataPath(userHome);
    Files.createDirectories(extensionJarPath.getParent());
    createFatJar(extensionJarPath);
    Files.writeString(metadataPath, "distribution: company-extension");
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    assertThatThrownBy(repository::activeRuntimeSelection)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Invalid distribution in runtime metadata file: " + metadataPath);
  }

  @Test
  void shouldFailWhenMetadataDistributionIdIsInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path extensionJarPath = activeExtensionJarPath(userHome);
    Path metadataPath = activeMetadataPath(userHome);
    Files.createDirectories(extensionJarPath.getParent());
    createFatJar(extensionJarPath);
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: " "
        version: 1.0.0
      """
    );
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    assertThatThrownBy(repository::activeRuntimeSelection)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Invalid distribution.id in runtime metadata file: " + metadataPath);
  }

  @Test
  void shouldFailWhenMetadataDistributionVersionIsInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path extensionJarPath = activeExtensionJarPath(userHome);
    Path metadataPath = activeMetadataPath(userHome);
    Files.createDirectories(extensionJarPath.getParent());
    createFatJar(extensionJarPath);
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: company-extension
        version: 1.0
      """
    );
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    assertThatThrownBy(repository::activeRuntimeSelection)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Invalid distribution.version in runtime metadata file: " + metadataPath);
  }

  @Test
  void shouldPreserveCauseWhenMetadataCannotBeParsed() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-selection-");
    Path extensionJarPath = activeExtensionJarPath(userHome);
    Path metadataPath = activeMetadataPath(userHome);
    Files.createDirectories(extensionJarPath.getParent());
    createFatJar(extensionJarPath);
    Files.writeString(metadataPath, "distribution: [broken");
    FileSystemRuntimeExtensionSelectionRepository repository = repository(userHome);

    assertThatThrownBy(repository::activeRuntimeSelection)
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Invalid runtime metadata file: " + metadataPath)
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(YAMLException.class);
  }

  private static FileSystemRuntimeExtensionSelectionRepository repository(Path userHome) {
    return new FileSystemRuntimeExtensionSelectionRepository(new Seed4JCliHome(userHome), new JarRuntimeExtensionPackageValidator());
  }

  private static String metadata(String distributionId, String distributionVersion) {
    return """
    distribution:
      id: %s
      version: %s
    """.formatted(distributionId, distributionVersion);
  }

  private static Path activeExtensionJarPath(Path userHome) {
    return userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
  }

  private static Path activeMetadataPath(Path userHome) {
    return userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
  }

  private static void createFatJar(Path jarPath) throws IOException {
    createJar(jarPath, "BOOT-INF/", "BOOT-INF/classes/");
  }

  private static void createJar(Path jarPath, String... entries) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      for (String entry : entries) {
        jarOutputStream.putNextEntry(new JarEntry(entry));
        if (!entry.endsWith("/")) {
          jarOutputStream.write(new byte[] { 1 });
        }
        jarOutputStream.closeEntry();
      }
    }
  }

  private static void createFlatJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("com/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("com/company/Extension.class"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
    }
  }
}
