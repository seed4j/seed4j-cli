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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionOverlayCacheTest {

  @Test
  void shouldMaterializeBootInfClassesInsideStableHashBasedCacheDirectory() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = ExtensionRuntimeFixture.createListExtensionModuleJar(Files.createTempFile("company-extension-", ".jar"));
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(userHome);

    Path overlayClassesPath = overlayCache.materialize(extensionJarPath);

    assertThat(overlayClassesPath)
      .exists()
      .isDirectory()
      .startsWith(userHome.resolve(".config/seed4j-cli/runtime/cache"))
      .endsWith(Path.of("classes"));
    assertThat(
      overlayClassesPath.resolve("com/seed4j/cli/bootstrap/domain/runtimeextension/list/RuntimeExtensionListOnlyModuleSlug.class")
    ).exists();
  }

  @Test
  void shouldReuseExistingCacheWithoutReextractingOverlayContent() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = ExtensionRuntimeFixture.createListExtensionModuleJar(Files.createTempFile("company-extension-", ".jar"));
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(userHome);
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
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(userHome);

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
    Path extensionJarPath = createFatJarWithConflictingClassEntryPaths(Files.createTempFile("company-extension-", ".jar"));
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(userHome);

    assertThatThrownBy(() -> overlayCache.materialize(extensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not materialize runtime extension overlay cache for " + extensionJarPath + ":");

    Path runtimeCacheDirectoryPath = userHome.resolve(".config/seed4j-cli/runtime/cache");
    if (Files.exists(runtimeCacheDirectoryPath)) {
      try (Stream<Path> cacheDirectoryEntries = Files.list(runtimeCacheDirectoryPath)) {
        assertThat(cacheDirectoryEntries).isEmpty();
      }
    }
  }

  @Test
  void shouldFailGracefullyWhenRuntimeCacheRootCannotBeCreated() throws IOException {
    Path userHomeFile = Files.createTempFile("seed4j-cli-user-home-", ".tmp");
    Path extensionJarPath = ExtensionRuntimeFixture.createListExtensionModuleJar(Files.createTempFile("company-extension-", ".jar"));
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(userHomeFile);

    assertThatThrownBy(() -> overlayCache.materialize(extensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not materialize runtime extension overlay cache for " + extensionJarPath + ":");
  }

  @Test
  void shouldMaterializeJarWhenBootInfClassesEntryDoesNotHaveTrailingSlash() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = createFatJarWithBootInfClassesEntryWithoutTrailingSlash(Files.createTempFile("company-extension-", ".jar"));
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(userHome);

    Path overlayClassesPath = overlayCache.materialize(extensionJarPath);

    assertThat(overlayClassesPath.resolve("com/example/Demo.class")).exists();
  }

  @Test
  void shouldRejectPathTraversalEntryInsideBootInfClasses() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = createFatJarWithPathTraversalEntryInsideClasses(Files.createTempFile("company-extension-", ".jar"));
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(userHome);

    assertThatThrownBy(() -> overlayCache.materialize(extensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Invalid runtime extension entry path: BOOT-INF/classes/../../outside.class");
  }

  @Test
  void shouldFilterGlobalRuntimeResourcesAndKeepFunctionalResourcesInOverlay() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = createFatJarWithGlobalAndFunctionalResources(Files.createTempFile("company-extension-", ".jar"));
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(userHome);

    Path overlayClassesPath = overlayCache.materialize(extensionJarPath);

    assertThat(overlayClassesPath.resolve("config/application.yml")).doesNotExist();
    assertThat(overlayClassesPath.resolve("config/application-prod.yaml")).doesNotExist();
    assertThat(overlayClassesPath.resolve("config/application.properties")).doesNotExist();
    assertThat(overlayClassesPath.resolve("logback-spring.xml")).doesNotExist();
    assertThat(overlayClassesPath.resolve("generator/runtime-extension/messages/template.yaml")).exists().hasContent("template-content");
  }

  @Test
  void shouldKeepNonXmlRootLogbackResourcesInOverlay() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = createFatJarWithGlobalAndFunctionalResourcesAndNonXmlRootLogback(
      Files.createTempFile("company-extension-", ".jar")
    );
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(userHome);

    Path overlayClassesPath = overlayCache.materialize(extensionJarPath);

    assertThat(overlayClassesPath.resolve("logback-spring.txt")).exists().hasContent("not-xml-logback-resource");
  }

  private static Path createFatJarWithConflictingClassEntryPaths(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/conflict"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/conflict/child.class"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static Path createFatJarWithBootInfClassesEntryWithoutTrailingSlash(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/ "));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/com/example/Demo.class"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static Path createFatJarWithPathTraversalEntryInsideClasses(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/../../outside.class"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static Path createFatJarWithGlobalAndFunctionalResources(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/config/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/generator/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/generator/runtime-extension/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/generator/runtime-extension/messages/"));
      jarOutputStream.closeEntry();

      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/config/application.yml"));
      jarOutputStream.write("name: ext".getBytes());
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/config/application-prod.yaml"));
      jarOutputStream.write("name: ext".getBytes());
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/config/application.properties"));
      jarOutputStream.write("name=ext".getBytes());
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/logback-spring.xml"));
      jarOutputStream.write("<configuration/>".getBytes());
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/generator/runtime-extension/messages/template.yaml"));
      jarOutputStream.write("template-content".getBytes());
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static Path createFatJarWithGlobalAndFunctionalResourcesAndNonXmlRootLogback(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/config/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/generator/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/generator/runtime-extension/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/generator/runtime-extension/messages/"));
      jarOutputStream.closeEntry();

      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/config/application.yml"));
      jarOutputStream.write("name: ext".getBytes());
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/config/application-prod.yaml"));
      jarOutputStream.write("name: ext".getBytes());
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/config/application.properties"));
      jarOutputStream.write("name=ext".getBytes());
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/logback-spring.xml"));
      jarOutputStream.write("<configuration/>".getBytes());
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/logback-spring.txt"));
      jarOutputStream.write("not-xml-logback-resource".getBytes());
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/generator/runtime-extension/messages/template.yaml"));
      jarOutputStream.write("template-content".getBytes());
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }
}
