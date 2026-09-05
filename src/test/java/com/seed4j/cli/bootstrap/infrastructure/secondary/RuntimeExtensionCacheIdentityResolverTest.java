package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionCacheIdentity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@UnitTest
class RuntimeExtensionCacheIdentityResolverTest {

  @Test
  void shouldResolveExpectedCacheIdentityFromExtensionContent(@TempDir Path temporaryDirectory) throws IOException {
    Path extensionJarPath = extensionJar(temporaryDirectory, "extension-a.jar", "seed4j-extension-a");
    RuntimeExtensionCacheIdentityResolver cacheIdentityResolver = new RuntimeExtensionCacheIdentityResolver();

    RuntimeExtensionCacheIdentity cacheIdentity = cacheIdentityResolver.resolve(extensionJarPath);

    assertThat(cacheIdentity.value()).isEqualTo("overlay-v1-63ec4ad5fe12603f3ebb0122a894885715d2d19432b9375022bc8acbb492de5b");
  }

  @Test
  void shouldResolveEqualCacheIdentitiesForFilesWithEqualContent(@TempDir Path temporaryDirectory) throws IOException {
    Path firstExtensionJarPath = extensionJar(temporaryDirectory, "extension-a.jar", "seed4j-extension-a");
    Path secondExtensionJarPath = extensionJar(temporaryDirectory, "copy-of-extension-a.jar", "seed4j-extension-a");
    RuntimeExtensionCacheIdentityResolver cacheIdentityResolver = new RuntimeExtensionCacheIdentityResolver();

    RuntimeExtensionCacheIdentity firstCacheIdentity = cacheIdentityResolver.resolve(firstExtensionJarPath);
    RuntimeExtensionCacheIdentity secondCacheIdentity = cacheIdentityResolver.resolve(secondExtensionJarPath);

    assertThat(secondCacheIdentity).isEqualTo(firstCacheIdentity);
  }

  @Test
  void shouldResolveDifferentCacheIdentitiesForDifferentContent(@TempDir Path temporaryDirectory) throws IOException {
    Path firstExtensionJarPath = extensionJar(temporaryDirectory, "extension-a.jar", "seed4j-extension-a");
    Path secondExtensionJarPath = extensionJar(temporaryDirectory, "extension-b.jar", "seed4j-extension-b");
    RuntimeExtensionCacheIdentityResolver cacheIdentityResolver = new RuntimeExtensionCacheIdentityResolver();

    RuntimeExtensionCacheIdentity firstCacheIdentity = cacheIdentityResolver.resolve(firstExtensionJarPath);
    RuntimeExtensionCacheIdentity secondCacheIdentity = cacheIdentityResolver.resolve(secondExtensionJarPath);

    assertThat(secondCacheIdentity).isNotEqualTo(firstCacheIdentity);
  }

  @Test
  void shouldReportTechnicalDetailsWhenExtensionJarCannotBeRead() throws IOException {
    Path unreadableExtensionJarPath = Files.createTempDirectory("seed4j-cli-extension-not-a-jar-");
    RuntimeExtensionCacheIdentityResolver cacheIdentityResolver = new RuntimeExtensionCacheIdentityResolver();

    assertThatThrownBy(() -> cacheIdentityResolver.resolve(unreadableExtensionJarPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not calculate runtime extension cache identity for " + unreadableExtensionJarPath + ".")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);
  }

  private static Path extensionJar(Path temporaryDirectory, String filename, String content) throws IOException {
    return Files.writeString(temporaryDirectory.resolve(filename), content);
  }
}
