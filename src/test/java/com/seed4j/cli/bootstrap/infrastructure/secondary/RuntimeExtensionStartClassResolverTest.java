package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionStartClassResolverTest {

  @Test
  void shouldFailWhenStartClassIsBlankInManifest() throws IOException {
    Path extensionJarPath = createExtensionJarWithStartClass(Files.createTempFile("seed4j-cli-extension-", ".jar"), "   ");
    RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver = new RuntimeExtensionStartClassResolver();

    assertThatThrownBy(() -> runtimeExtensionStartClassResolver.resolve(extensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Missing manifest Start-Class");
  }

  @Test
  void shouldFailWhenExtensionJarCannotBeRead() throws IOException {
    Path invalidExtensionJarPath = Files.createTempDirectory("seed4j-cli-extension-not-a-jar-");
    RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver = new RuntimeExtensionStartClassResolver();

    assertThatThrownBy(() -> runtimeExtensionStartClassResolver.resolve(invalidExtensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not read manifest Start-Class")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldFailWhenManifestIsMissingFromExtensionJar() throws IOException {
    Path extensionJarPath = createExtensionJarWithoutManifest(Files.createTempFile("seed4j-cli-extension-", ".jar"));
    RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver = new RuntimeExtensionStartClassResolver();

    assertThatThrownBy(() -> runtimeExtensionStartClassResolver.resolve(extensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Missing manifest Start-Class");
  }

  @Test
  void shouldResolveStartClassFromManifestWhenPresent() throws IOException {
    Path extensionJarPath = createExtensionJarWithStartClass(
      Files.createTempFile("seed4j-cli-extension-", ".jar"),
      "com.seed4j.extension.ExtensionApplication"
    );
    RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver = new RuntimeExtensionStartClassResolver();

    String startClass = runtimeExtensionStartClassResolver.resolve(extensionJarPath);

    assertThat(startClass).isEqualTo("com.seed4j.extension.ExtensionApplication");
  }

  @Test
  void shouldFailWhenStartClassIsMissingFromManifest() throws IOException {
    Path extensionJarPath = createExtensionJarWithoutStartClass(Files.createTempFile("seed4j-cli-extension-", ".jar"));
    RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver = new RuntimeExtensionStartClassResolver();

    assertThatThrownBy(() -> runtimeExtensionStartClassResolver.resolve(extensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Start-Class");
  }

  @Test
  void shouldFailWhenStartClassClassFileIsMissingFromBootInfClasses() throws IOException {
    Path extensionJarPath = createExtensionJarWithMissingStartClassClassFile(
      Files.createTempFile("seed4j-cli-extension-", ".jar"),
      "com.seed4j.extension.ExtensionApplication"
    );
    RuntimeExtensionStartClassResolver runtimeExtensionStartClassResolver = new RuntimeExtensionStartClassResolver();

    assertThatThrownBy(() -> runtimeExtensionStartClassResolver.resolve(extensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Start-Class")
      .hasMessageContaining("BOOT-INF/classes");
  }

  private static Path createExtensionJarWithStartClass(Path extensionJarPath, String startClass) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Start-Class", startClass);
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(extensionJarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/com/seed4j/extension/ExtensionApplication.class"));
      jarOutputStream.write(new byte[] { 0 });
      jarOutputStream.closeEntry();
    }
    return extensionJarPath;
  }

  private static Path createExtensionJarWithoutStartClass(Path extensionJarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(extensionJarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
    }
    return extensionJarPath;
  }

  private static Path createExtensionJarWithMissingStartClassClassFile(Path extensionJarPath, String startClass) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Start-Class", startClass);
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(extensionJarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
    }
    return extensionJarPath;
  }

  private static Path createExtensionJarWithoutManifest(Path extensionJarPath) throws IOException {
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(extensionJarPath))) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
    }
    return extensionJarPath;
  }
}
