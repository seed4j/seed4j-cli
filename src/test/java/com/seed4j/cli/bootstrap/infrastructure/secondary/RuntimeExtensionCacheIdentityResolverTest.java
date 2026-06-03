package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionCacheIdentityResolverTest {

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
}
