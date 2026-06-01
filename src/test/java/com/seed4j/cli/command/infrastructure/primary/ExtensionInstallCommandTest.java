package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.RuntimeExtensionApplicationService;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

@UnitTest
class ExtensionInstallCommandTest {

  private static final String DISTRIBUTION_ID = "company-extension";
  private static final String DISTRIBUTION_VERSION = "1.0.0";

  @Test
  void shouldListInstallSubcommandWhenShowingExtensionHelp() {
    Path userHome = Path.of(System.getProperty("user.home"));
    ExtensionInstallCommand installCommand = installCommand(userHome);
    ExtensionCommand extensionCommand = new ExtensionCommand(installCommand);

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      CommandLine commandLine = new CommandLine(extensionCommand.spec());
      int exitCode = commandLine.execute("--help");

      assertThat(exitCode).isZero();
      assertThat(outputCaptor.getStandardOutput()).contains("install");
    }
  }

  @Test
  void shouldInstallExtensionRuntimeAndPrintRuntimePathsAndValidationHintsWhenInputsAreValid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-command-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    ExtensionInstallCommand installCommand = installCommand(userHome);
    ExtensionCommand extensionCommand = new ExtensionCommand(installCommand);
    String[] args = {
      "install",
      extensionJarPath.toString(),
      "--distribution-id",
      DISTRIBUTION_ID,
      "--distribution-version",
      DISTRIBUTION_VERSION,
    };

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      CommandLine commandLine = new CommandLine(extensionCommand.spec());
      int exitCode = commandLine.execute(args);

      assertThat(exitCode).isZero();
      assertThat(outputCaptor.getStandardOutput())
        .contains("Extension runtime installed")
        .contains("Runtime jar: " + userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar"))
        .contains("Metadata: " + userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml"))
        .contains("Config: " + userHome.resolve(".config/seed4j-cli/config.yml"))
        .contains("seed4j --version")
        .contains("seed4j list");
    }
  }

  @Test
  void shouldWarnRuntimeReplacementWhenActiveRuntimeAlreadyExists() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-command-");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(runtimeJarPath.getParent());
    createFatJar(runtimeJarPath);
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: old-extension
        version: 0.1.0
      """
    );
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    ExtensionInstallCommand installCommand = installCommand(userHome);
    ExtensionCommand extensionCommand = new ExtensionCommand(installCommand);
    String[] args = {
      "install",
      extensionJarPath.toString(),
      "--distribution-id",
      DISTRIBUTION_ID,
      "--distribution-version",
      DISTRIBUTION_VERSION,
    };

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      CommandLine commandLine = new CommandLine(extensionCommand.spec());
      int exitCode = commandLine.execute(args);

      assertThat(exitCode).isZero();
      assertThat(outputCaptor.getStandardOutput()).contains("Replaced active runtime extension.");
    }
  }

  @Test
  void shouldReturnNonZeroAndShowObjectiveErrorWhenConfigIsInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-command-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j: [broken");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    ExtensionInstallCommand installCommand = installCommand(userHome);
    ExtensionCommand extensionCommand = new ExtensionCommand(installCommand);
    String[] args = {
      "install",
      extensionJarPath.toString(),
      "--distribution-id",
      DISTRIBUTION_ID,
      "--distribution-version",
      DISTRIBUTION_VERSION,
    };

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      CommandLine commandLine = new CommandLine(extensionCommand.spec());
      int exitCode = commandLine.execute(args);

      assertThat(exitCode).isNotZero();
      assertThat(outputCaptor.getStandardError()).contains("Could not read ~/.config/seed4j-cli/config.yml.").contains("Details:");
    }
  }

  @Test
  void shouldShowRequiredOptionLabelsWhenDistributionOptionsAreMissing() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-command-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    ExtensionInstallCommand installCommand = installCommand(userHome);
    ExtensionCommand extensionCommand = new ExtensionCommand(installCommand);
    String[] args = { "install", extensionJarPath.toString() };

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      CommandLine commandLine = new CommandLine(extensionCommand.spec());
      int exitCode = commandLine.execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(outputCaptor.getStandardError()).contains("'--distribution-id=<id*>'").contains("'--distribution-version=<version*>'");
    }
  }

  @Test
  void shouldPersistRuntimeArtifactsAndConfigFileWhenInstallCommandSucceeds() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-command-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    ExtensionInstallCommand installCommand = installCommand(userHome);
    ExtensionCommand extensionCommand = new ExtensionCommand(installCommand);
    String[] args = {
      "install",
      extensionJarPath.toString(),
      "--distribution-id",
      DISTRIBUTION_ID,
      "--distribution-version",
      DISTRIBUTION_VERSION,
    };

    CommandLine commandLine = new CommandLine(extensionCommand.spec());
    int exitCode = commandLine.execute(args);

    assertThat(exitCode).isZero();
    assertThat(configPath).exists();
    assertThat(Files.readString(configPath)).contains("mode: extension");
    assertThat(runtimeJarPath).exists();
    assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(metadataPath).exists();
    assertThat(Files.readString(metadataPath)).contains("id: " + DISTRIBUTION_ID).contains("version: " + DISTRIBUTION_VERSION);
  }

  private static ExtensionInstallCommand installCommand(Path userHome) {
    return new ExtensionInstallCommand(runtimeExtensionApplicationService(userHome));
  }

  private static RuntimeExtensionApplicationService runtimeExtensionApplicationService(Path userHome) {
    return new RuntimeExtensionApplicationService(
      userHome,
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      new FileSystemRuntimeExtensionArtifactsRepository()
    );
  }

  private static Path createFatJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
    }

    return jarPath;
  }
}
