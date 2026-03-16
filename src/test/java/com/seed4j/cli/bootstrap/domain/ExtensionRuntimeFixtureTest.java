package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

@UnitTest
class ExtensionRuntimeFixtureTest {

  @Test
  void shouldInstallAValidExtensionRuntimeFixture() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");

    ExtensionRuntimeFixture.ExtensionRuntimeFixturePaths fixturePaths = ExtensionRuntimeFixture.install(userHome);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(fixturePaths.extensionJarPath(), fixturePaths.metadataPath())
    );

    assertThat(fixturePaths.configFilePath()).exists();
    assertThat(fixturePaths.metadataPath()).exists();
    assertThat(fixturePaths.extensionJarPath()).exists();

    try (JarFile ignored = new JarFile(fixturePaths.extensionJarPath().toFile())) {
      RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, "0.0.1-SNAPSHOT");
      assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
      assertThat(runtimeSelection.distributionId()).contains("company-extension");
      assertThat(runtimeSelection.distributionVersion()).contains("1.0.0");
    }
  }
}
