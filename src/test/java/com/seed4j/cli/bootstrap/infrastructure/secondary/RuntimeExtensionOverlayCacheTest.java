package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@UnitTest
class RuntimeExtensionOverlayCacheTest {

  @Nested
  class CacheLifecycle {

    @Test
    void shouldMaterializeBootInfClassesInsideStableHashBasedCacheDirectory(@TempDir Path temporaryDirectory) throws IOException {
      Path userHome = temporaryDirectory.resolve("user-home");
      OverlayCacheFixture fixture = new OverlayCacheFixture(temporaryDirectory, userHome);
      Path extensionJarPath = fixture.createListExtensionModuleJar();
      Path expectedClassPath = Path.of("com/mycompany/seed4j/extension/runtime/main/list/RuntimeExtensionListOnlyModuleSlug.class");

      Path overlayClassesPath = fixture.overlayCache.materialize(extensionJarPath);

      assertThat(overlayClassesPath)
        .exists()
        .isDirectory()
        .startsWith(userHome.resolve(".config/seed4j-cli/runtime/cache"))
        .endsWith(Path.of("classes"));
      assertThat(overlayClassesPath.resolve(expectedClassPath)).exists();
    }

    @Test
    void shouldReuseExistingCacheWithoutReextractingOverlayContent(@TempDir Path temporaryDirectory) throws IOException {
      OverlayCacheFixture fixture = new OverlayCacheFixture(temporaryDirectory, temporaryDirectory.resolve("user-home"));
      Path extensionJarPath = fixture.createListExtensionModuleJar();
      Path firstOverlayClassesPath = fixture.overlayCache.materialize(extensionJarPath);
      Path cacheMarkerPath = firstOverlayClassesPath.resolve("cache-hit.marker");
      Files.writeString(cacheMarkerPath, "existing-overlay-content");

      Path secondOverlayClassesPath = fixture.overlayCache.materialize(extensionJarPath);

      assertThat(secondOverlayClassesPath).isEqualTo(firstOverlayClassesPath);
      assertThat(cacheMarkerPath).exists().hasContent("existing-overlay-content");
    }

    @Test
    void shouldDeleteStagingDirectoryWhenOverlayMaterializationFails(@TempDir Path temporaryDirectory) throws IOException {
      Path userHome = temporaryDirectory.resolve("user-home");
      OverlayCacheFixture fixture = new OverlayCacheFixture(temporaryDirectory, userHome);
      Path extensionJarPath = fixture.createFlatJar();

      assertThatThrownBy(() -> fixture.overlayCache.materialize(extensionJarPath))
        .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
        .hasMessageContaining("BOOT-INF/classes");

      assertThat(fixture.runtimeCacheEntries()).isEmpty();
    }

    @Test
    void shouldDeleteStagingDirectoryWhenOverlayMaterializationFailsWithIOException(@TempDir Path temporaryDirectory) throws IOException {
      Path userHome = temporaryDirectory.resolve("user-home");
      OverlayCacheFixture fixture = new OverlayCacheFixture(temporaryDirectory, userHome);
      Path extensionJarPath = fixture.createExtensionJar(
        new TestJarEntry("BOOT-INF/", new byte[0]),
        new TestJarEntry("BOOT-INF/classes/", new byte[0]),
        new TestJarEntry("BOOT-INF/classes/conflict", new byte[] { 1 }),
        new TestJarEntry("BOOT-INF/classes/conflict/child.class", new byte[] { 1 })
      );

      assertThatThrownBy(() -> fixture.overlayCache.materialize(extensionJarPath))
        .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
        .hasMessageContaining("Could not materialize runtime extension overlay cache for " + extensionJarPath + ".")
        .hasMessageContaining("Details:")
        .hasCauseInstanceOf(IOException.class);

      assertThat(fixture.runtimeCacheEntries()).isEmpty();
    }

    @Test
    void shouldFailGracefullyWhenRuntimeCacheRootCannotBeCreated(@TempDir Path temporaryDirectory) throws IOException {
      Path userHomeFile = Files.createFile(temporaryDirectory.resolve("user-home.tmp"));
      OverlayCacheFixture fixture = new OverlayCacheFixture(temporaryDirectory, userHomeFile);
      Path extensionJarPath = fixture.createListExtensionModuleJar();

      assertThatThrownBy(() -> fixture.overlayCache.materialize(extensionJarPath))
        .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
        .hasMessageContaining("Could not materialize runtime extension overlay cache for " + extensionJarPath + ".")
        .hasMessageContaining("Details:")
        .hasCauseInstanceOf(IOException.class);
    }
  }

  @Nested
  class Materialization {

    @Test
    void shouldMaterializeJarWhenBootInfClassesEntryDoesNotHaveTrailingSlash(@TempDir Path temporaryDirectory) throws IOException {
      OverlayCacheFixture fixture = new OverlayCacheFixture(temporaryDirectory, temporaryDirectory.resolve("user-home"));
      Path extensionJarPath = fixture.createExtensionJar(
        new TestJarEntry("BOOT-INF/", new byte[0]),
        new TestJarEntry("BOOT-INF/classes", new byte[0]),
        new TestJarEntry("BOOT-INF/classes/ ", new byte[0]),
        new TestJarEntry("BOOT-INF/classes/com/example/Demo.class", new byte[] { 1 })
      );

      Path overlayClassesPath = fixture.overlayCache.materialize(extensionJarPath);

      assertThat(overlayClassesPath.resolve("com/example/Demo.class")).exists();
    }

    @Test
    void shouldRejectPathTraversalEntryInsideBootInfClasses(@TempDir Path temporaryDirectory) throws IOException {
      OverlayCacheFixture fixture = new OverlayCacheFixture(temporaryDirectory, temporaryDirectory.resolve("user-home"));
      Path extensionJarPath = fixture.createExtensionJar(
        new TestJarEntry("BOOT-INF/", new byte[0]),
        new TestJarEntry("BOOT-INF/classes/", new byte[0]),
        new TestJarEntry("BOOT-INF/classes/../../outside.class", new byte[] { 1 })
      );

      assertThatThrownBy(() -> fixture.overlayCache.materialize(extensionJarPath))
        .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
        .hasMessageContaining("Invalid runtime extension entry path: BOOT-INF/classes/../../outside.class");
    }
  }

  @Nested
  class ResourceFiltering {

    @Test
    void shouldFilterGlobalRuntimeResourcesAndKeepFunctionalResourcesInOverlay(@TempDir Path temporaryDirectory) throws IOException {
      OverlayCacheFixture fixture = new OverlayCacheFixture(temporaryDirectory, temporaryDirectory.resolve("user-home"));
      Path extensionJarPath = fixture.createExtensionJar(
        TestJarEntry.resource("config/application.yml", "name: ext"),
        TestJarEntry.resource("config/application-prod.yaml", "name: ext"),
        TestJarEntry.resource("config/application.properties", "name=ext"),
        TestJarEntry.resource("logback-spring.xml", "<configuration/>"),
        TestJarEntry.resource("generator/runtime-extension/messages/template.yaml", "template-content")
      );

      Path overlayClassesPath = fixture.overlayCache.materialize(extensionJarPath);

      assertThat(overlayClassesPath.resolve("config/application.yml")).doesNotExist();
      assertThat(overlayClassesPath.resolve("config/application-prod.yaml")).doesNotExist();
      assertThat(overlayClassesPath.resolve("config/application.properties")).doesNotExist();
      assertThat(overlayClassesPath.resolve("logback-spring.xml")).doesNotExist();
      assertThat(overlayClassesPath.resolve("generator/runtime-extension/messages/template.yaml")).exists().hasContent("template-content");
    }

    @Test
    void shouldKeepNonXmlRootLogbackResourcesInOverlay(@TempDir Path temporaryDirectory) throws IOException {
      OverlayCacheFixture fixture = new OverlayCacheFixture(temporaryDirectory, temporaryDirectory.resolve("user-home"));
      Path extensionJarPath = fixture.createExtensionJar(TestJarEntry.resource("logback-spring.txt", "not-xml-logback-resource"));

      Path overlayClassesPath = fixture.overlayCache.materialize(extensionJarPath);

      assertThat(overlayClassesPath.resolve("logback-spring.txt")).exists().hasContent("not-xml-logback-resource");
    }

    @Test
    void shouldKeepApplicationPrefixedResourceWithNonConfigurationSuffix(@TempDir Path temporaryDirectory) throws IOException {
      OverlayCacheFixture fixture = new OverlayCacheFixture(temporaryDirectory, temporaryDirectory.resolve("user-home"));
      Path extensionJarPath = fixture.createExtensionJar(TestJarEntry.resource("config/application-prod.txt", "not-configuration"));

      Path overlayClassesPath = fixture.overlayCache.materialize(extensionJarPath);

      assertThat(overlayClassesPath.resolve("config/application-prod.txt")).exists().hasContent("not-configuration");
    }
  }

  private static final class OverlayCacheFixture {

    private final Path jarPath;
    private final Path runtimeCacheDirectoryPath;
    private final RuntimeExtensionOverlayCache overlayCache;

    private OverlayCacheFixture(Path temporaryDirectory, Path userHome) {
      jarPath = temporaryDirectory.resolve("company-extension.jar");
      runtimeCacheDirectoryPath = userHome.resolve(".config/seed4j-cli/runtime/cache");
      overlayCache = new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHome));
    }

    private Path createListExtensionModuleJar() throws IOException {
      return ExtensionRuntimeFixture.createListExtensionModuleJar(jarPath);
    }

    private Path createFlatJar() throws IOException {
      return ExtensionRuntimeFixture.createFlatJar(jarPath);
    }

    private Path createExtensionJar(TestJarEntry... entries) throws IOException {
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

    private List<Path> runtimeCacheEntries() throws IOException {
      if (Files.notExists(runtimeCacheDirectoryPath)) {
        return List.of();
      }

      try (Stream<Path> cacheDirectoryEntries = Files.list(runtimeCacheDirectoryPath)) {
        return cacheDirectoryEntries.toList();
      }
    }
  }

  private record TestJarEntry(String name, byte[] content) {
    private static TestJarEntry resource(String path, String content) {
      return new TestJarEntry("BOOT-INF/classes/" + path, content.getBytes(StandardCharsets.UTF_8));
    }
  }
}
