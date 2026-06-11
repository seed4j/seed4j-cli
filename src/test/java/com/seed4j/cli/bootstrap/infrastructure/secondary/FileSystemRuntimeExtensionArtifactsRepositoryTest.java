package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

@UnitTest
class FileSystemRuntimeExtensionArtifactsRepositoryTest {

  private static final String DISTRIBUTION_ID = "company-extension";
  private static final String DISTRIBUTION_VERSION = "1.0.0";

  @Test
  void shouldNotLeaveUnexpectedFilesWhenRuntimeJarPublicationFails() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-artifacts-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path runtimeJarPath = runtimeJarPath(userHome);
    Files.createDirectories(runtimeJarPath);
    Files.writeString(runtimeJarPath.resolve("occupied.txt"), "existing");
    FileSystemRuntimeExtensionArtifactsRepository repository = repository(userHome);

    assertThatThrownBy(() -> repository.install(installRequest(extensionJarPath)))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not install runtime extension.")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);

    assertThat(runtimeJarPath).isDirectory();
    assertThat(runtimeJarPath.resolve("occupied.txt")).exists();
    assertThat(metadataPath(userHome)).doesNotExist();
    assertThat(activeRuntimeFileNames(userHome)).containsExactly("extension.jar");
  }

  @Test
  void shouldNotLeaveUnexpectedFilesWhenMetadataPublicationFails() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-artifacts-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path metadataPath = metadataPath(userHome);
    Files.createDirectories(metadataPath);
    Files.writeString(metadataPath.resolve("occupied.txt"), "existing");
    FileSystemRuntimeExtensionArtifactsRepository repository = repository(userHome);

    assertThatThrownBy(() -> repository.install(installRequest(extensionJarPath)))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not install runtime extension.")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);

    assertThat(Files.readAllBytes(runtimeJarPath(userHome))).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(metadataPath).isDirectory();
    assertThat(metadataPath.resolve("occupied.txt")).exists();
    assertThat(activeRuntimeFileNames(userHome)).containsExactlyInAnyOrder("extension.jar", "metadata.yml");
  }

  @Test
  void shouldDetectActiveRuntimeWhenOnlyRuntimeJarExists() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-artifacts-");
    Path runtimeJarPath = runtimeJarPath(userHome);
    Files.createDirectories(runtimeJarPath.getParent());
    createFatJar(runtimeJarPath);
    FileSystemRuntimeExtensionArtifactsRepository repository = repository(userHome);

    assertThat(repository.activeRuntimePresent()).isTrue();
  }

  @Test
  void shouldDetectActiveRuntimeWhenOnlyMetadataExists() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-artifacts-");
    Path metadataPath = metadataPath(userHome);
    Files.createDirectories(metadataPath.getParent());
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: legacy-extension
        version: 0.9.0
      """
    );
    FileSystemRuntimeExtensionArtifactsRepository repository = repository(userHome);

    assertThat(repository.activeRuntimePresent()).isTrue();
  }

  private static RuntimeExtensionInstallRequest installRequest(Path extensionJarPath) {
    return new RuntimeExtensionInstallRequest(
      RuntimeExtensionJarPath.from(extensionJarPath.toString()),
      new RuntimeDistributionId(DISTRIBUTION_ID),
      new RuntimeDistributionVersion(DISTRIBUTION_VERSION)
    );
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

  private static FileSystemRuntimeExtensionArtifactsRepository repository(Path userHome) {
    return new FileSystemRuntimeExtensionArtifactsRepository(new Seed4JCliHome(userHome));
  }

  private static List<String> activeRuntimeFileNames(Path userHome) throws IOException {
    try (Stream<Path> paths = Files.list(activeRuntimeDirectory(userHome))) {
      return paths
        .map(path -> path.getFileName().toString())
        .sorted()
        .toList();
    }
  }

  private static Path activeRuntimeDirectory(Path userHome) {
    return userHome.resolve(".config/seed4j-cli/runtime/active");
  }

  private static Path runtimeJarPath(Path userHome) {
    return activeRuntimeDirectory(userHome).resolve("extension.jar");
  }

  private static Path metadataPath(Path userHome) {
    return activeRuntimeDirectory(userHome).resolve("metadata.yml");
  }
}
