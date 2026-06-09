package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.seed4j.extension.runtime.main.list.RuntimeExtensionListOnlyApplicationService;
import com.mycompany.seed4j.extension.runtime.main.list.RuntimeExtensionListOnlyModuleConfiguration;
import com.mycompany.seed4j.extension.runtime.main.list.RuntimeExtensionListOnlyModuleFactory;
import com.mycompany.seed4j.extension.runtime.main.list.RuntimeExtensionListOnlyModuleSlug;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionSelectionRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JarRuntimeExtensionPackageValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

@UnitTest
class ExtensionRuntimeFixtureTest {

  private static final String BOOT_INF_CLASSES_DIRECTORY = "BOOT-INF/classes/";

  @Test
  void shouldInstallAValidExtensionRuntimeFixtureArtifacts() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");

    ExtensionRuntimeFixture.ExtensionRuntimeFixturePaths fixturePaths = ExtensionRuntimeFixture.install(userHome);

    assertThat(fixturePaths.configFilePath()).exists();
    assertThat(fixturePaths.metadataPath()).exists();
    assertThat(fixturePaths.extensionJarPath()).exists();
    assertThat(jarEntries(fixturePaths.extensionJarPath())).contains("META-INF/MANIFEST.MF", BOOT_INF_CLASSES_DIRECTORY);
  }

  @Test
  void shouldResolveRuntimeSelectionFromAValidExtensionRuntimeFixture() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    ExtensionRuntimeFixture.install(userHome);

    RuntimeSelection runtimeSelection = runtimeExtensionSelectionRepository(userHome).activeRuntimeSelection();

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.distributionId()).contains(new RuntimeDistributionId("company-extension"));
    assertThat(runtimeSelection.distributionVersion()).contains(new RuntimeDistributionVersion("1.0.0"));
  }

  @Test
  void shouldInstallAnExtensionRuntimeFixtureWithListExtensionModuleArtifacts() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");

    ExtensionRuntimeFixture.ExtensionRuntimeFixturePaths fixturePaths = ExtensionRuntimeFixture.installWithListExtensionModule(userHome);

    List<String> extensionJarEntries = jarEntries(fixturePaths.extensionJarPath());
    assertThat(fixturePaths.configFilePath()).exists();
    assertThat(fixturePaths.metadataPath()).exists();
    assertThat(fixturePaths.extensionJarPath()).exists();
    assertThat(extensionJarEntries)
      .contains(
        bootInfClassEntryName(RuntimeExtensionListOnlyModuleSlug.class),
        bootInfClassEntryName(RuntimeExtensionListOnlyModuleFactory.class),
        bootInfClassEntryName(RuntimeExtensionListOnlyApplicationService.class),
        bootInfClassEntryName(RuntimeExtensionListOnlyModuleConfiguration.class)
      )
      .doesNotContain(classEntryName(RuntimeExtensionListOnlyModuleSlug.class))
      .doesNotContain("config/application.yml")
      .doesNotHaveDuplicates();
  }

  @Test
  void shouldResolveRuntimeSelectionFromAnExtensionRuntimeFixtureWithListExtensionModule() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    ExtensionRuntimeFixture.installWithListExtensionModule(userHome);

    RuntimeSelection runtimeSelection = runtimeExtensionSelectionRepository(userHome).activeRuntimeSelection();

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.distributionId()).contains(new RuntimeDistributionId("company-extension"));
    assertThat(runtimeSelection.distributionVersion()).contains(new RuntimeDistributionVersion("1.0.0"));
  }

  private static FileSystemRuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository(Path userHome) {
    return new FileSystemRuntimeExtensionSelectionRepository(new Seed4JCliHome(userHome), new JarRuntimeExtensionPackageValidator());
  }

  private static List<String> jarEntries(Path extensionJarPath) throws IOException {
    try (JarFile extensionJarFile = new JarFile(extensionJarPath.toFile())) {
      return extensionJarFile.stream().map(JarEntry::getName).toList();
    }
  }

  private static String classEntryName(Class<?> classType) {
    return classType.getName().replace('.', '/') + ".class";
  }

  private static String bootInfClassEntryName(Class<?> classType) {
    return BOOT_INF_CLASSES_DIRECTORY + classEntryName(classType);
  }
}
