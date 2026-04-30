package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
