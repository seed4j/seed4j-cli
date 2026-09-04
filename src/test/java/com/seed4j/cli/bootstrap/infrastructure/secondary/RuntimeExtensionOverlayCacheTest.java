package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionOverlayCacheTest {

  @Test
  void shouldMaterializeBootInfClassesInsideStableHashBasedCacheDirectory() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = ExtensionRuntimeFixture.createListExtensionModuleJar(Files.createTempFile("company-extension-", ".jar"));
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHome));

    Path overlayClassesPath = overlayCache.materialize(extensionJarPath);

    assertThat(overlayClassesPath)
      .exists()
      .isDirectory()
      .startsWith(userHome.resolve(".config/seed4j-cli/runtime/cache"))
      .endsWith(Path.of("classes"));
    assertThat(
      overlayClassesPath.resolve("com/mycompany/seed4j/extension/runtime/main/list/RuntimeExtensionListOnlyModuleSlug.class")
    ).exists();
  }

  @Test
  void shouldReuseExistingCacheWithoutReextractingOverlayContent() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = ExtensionRuntimeFixture.createListExtensionModuleJar(Files.createTempFile("company-extension-", ".jar"));
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHome));
    Path firstOverlayClassesPath = overlayCache.materialize(extensionJarPath);
    Path cacheMarkerPath = firstOverlayClassesPath.resolve("cache-hit.marker");
    Files.writeString(cacheMarkerPath, "existing-overlay-content");

    Path secondOverlayClassesPath = overlayCache.materialize(extensionJarPath);

    assertThat(secondOverlayClassesPath).isEqualTo(firstOverlayClassesPath);
    assertThat(cacheMarkerPath).exists().hasContent("existing-overlay-content");
  }

  @Test
  void shouldDeleteStagingDirectoryWhenOverlayMaterializationFails() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = ExtensionRuntimeFixture.createFlatJar(Files.createTempFile("company-extension-", ".jar"));
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHome));

    assertThatThrownBy(() -> overlayCache.materialize(extensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("BOOT-INF/classes");

    Path runtimeCacheDirectoryPath = userHome.resolve(".config/seed4j-cli/runtime/cache");
    if (Files.exists(runtimeCacheDirectoryPath)) {
      try (Stream<Path> cacheDirectoryEntries = Files.list(runtimeCacheDirectoryPath)) {
        assertThat(cacheDirectoryEntries).isEmpty();
      }
    }
  }

  @Test
  void shouldDeleteStagingDirectoryWhenOverlayMaterializationFailsWithIOException() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = createExtensionJar(
      Files.createTempFile("company-extension-", ".jar"),
      new TestJarEntry("BOOT-INF/", new byte[0]),
      new TestJarEntry("BOOT-INF/classes/", new byte[0]),
      new TestJarEntry("BOOT-INF/classes/conflict", new byte[] { 1 }),
      new TestJarEntry("BOOT-INF/classes/conflict/child.class", new byte[] { 1 })
    );
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHome));

    assertThatThrownBy(() -> overlayCache.materialize(extensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not materialize runtime extension overlay cache for " + extensionJarPath + ".")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);

    Path runtimeCacheDirectoryPath = userHome.resolve(".config/seed4j-cli/runtime/cache");
    if (Files.exists(runtimeCacheDirectoryPath)) {
      try (Stream<Path> cacheDirectoryEntries = Files.list(runtimeCacheDirectoryPath)) {
        assertThat(cacheDirectoryEntries).isEmpty();
      }
    }
  }

  private static Path createExtensionJar(Path jarPath, TestJarEntry... entries) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      for (TestJarEntry entry : entries) {
        jarOutputStream.putNextEntry(new JarEntry(entry.name()));
        jarOutputStream.write(entry.content());
        jarOutputStream.closeEntry();
      }
    }
    return jarPath;
  }

  private record TestJarEntry(String name, byte[] content) {}

  @Test
  void shouldFailGracefullyWhenRuntimeCacheRootCannotBeCreated() throws IOException {
    Path userHomeFile = Files.createTempFile("seed4j-cli-user-home-", ".tmp");
    Path extensionJarPath = ExtensionRuntimeFixture.createListExtensionModuleJar(Files.createTempFile("company-extension-", ".jar"));
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHomeFile));

    assertThatThrownBy(() -> overlayCache.materialize(extensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not materialize runtime extension overlay cache for " + extensionJarPath + ".")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldMaterializeJarWhenBootInfClassesEntryDoesNotHaveTrailingSlash() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = createExtensionJar(
      Files.createTempFile("company-extension-", ".jar"),
      new TestJarEntry("BOOT-INF/", new byte[0]),
      new TestJarEntry("BOOT-INF/classes", new byte[0]),
      new TestJarEntry("BOOT-INF/classes/ ", new byte[0]),
      new TestJarEntry("BOOT-INF/classes/com/example/Demo.class", new byte[] { 1 })
    );
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHome));

    Path overlayClassesPath = overlayCache.materialize(extensionJarPath);

    assertThat(overlayClassesPath.resolve("com/example/Demo.class")).exists();
  }

  @Test
  void shouldRejectPathTraversalEntryInsideBootInfClasses() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = createExtensionJar(
      Files.createTempFile("company-extension-", ".jar"),
      new TestJarEntry("BOOT-INF/", new byte[0]),
      new TestJarEntry("BOOT-INF/classes/", new byte[0]),
      new TestJarEntry("BOOT-INF/classes/../../outside.class", new byte[] { 1 })
    );
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHome));

    assertThatThrownBy(() -> overlayCache.materialize(extensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Invalid runtime extension entry path: BOOT-INF/classes/../../outside.class");
  }
}
