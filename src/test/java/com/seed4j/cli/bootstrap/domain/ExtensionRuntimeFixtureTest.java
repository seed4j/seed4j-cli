package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyApplicationService;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleConfiguration;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleFactory;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleSlug;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
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

  @Test
  void shouldInstallAnExtensionRuntimeFixtureWithListExtensionModule() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    ExtensionRuntimeFixture.ExtensionRuntimeFixturePaths fixturePaths = ExtensionRuntimeFixture.installWithListExtensionModule(userHome);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(fixturePaths.extensionJarPath(), fixturePaths.metadataPath())
    );

    assertThat(fixturePaths.configFilePath()).exists();
    assertThat(fixturePaths.metadataPath()).exists();
    assertThat(fixturePaths.extensionJarPath()).exists();
    assertThat(jarEntries(fixturePaths.extensionJarPath())).contains(
      classEntryName(RuntimeExtensionListOnlyModuleSlug.class),
      classEntryName(RuntimeExtensionListOnlyModuleFactory.class),
      classEntryName(RuntimeExtensionListOnlyApplicationService.class),
      classEntryName(RuntimeExtensionListOnlyModuleConfiguration.class)
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration, "0.0.1-SNAPSHOT");
    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.distributionId()).contains("company-extension");
    assertThat(runtimeSelection.distributionVersion()).contains("1.0.0");
  }

  private static List<String> jarEntries(Path extensionJarPath) throws IOException {
    try (JarFile extensionJarFile = new JarFile(extensionJarPath.toFile())) {
      return extensionJarFile.stream().map(JarEntry::getName).toList();
    }
  }

  private static String classEntryName(Class<?> classType) {
    return classType.getName().replace('.', '/') + ".class";
  }
}
