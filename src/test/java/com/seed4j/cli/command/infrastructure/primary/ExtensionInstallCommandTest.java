package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.UnitTest;
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
    ExtensionInstallCommand installCommand = new ExtensionInstallCommand(System.getProperty("user.home"));
    ExtensionCommand extensionCommand = new ExtensionCommand(installCommand);

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      CommandLine commandLine = new CommandLine(extensionCommand.spec());
      int exitCode = commandLine.execute("--help");

      assertThat(exitCode).isZero();
      assertThat(outputCaptor.getStandardOutput()).contains("install");
    }
  }

  @Test
  void shouldInstallExtensionRuntimeAndPrintValidationHintsWhenInputsAreValid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-command-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    ExtensionInstallCommand installCommand = new ExtensionInstallCommand(userHome.toString());
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
    ExtensionInstallCommand installCommand = new ExtensionInstallCommand(userHome.toString());
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
    ExtensionInstallCommand installCommand = new ExtensionInstallCommand(userHome.toString());
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
      assertThat(outputCaptor.getStandardError()).contains("Could not read ~/.config/seed4j-cli/config.yml.");
    }
  }

  @Test
  void shouldShowRequiredOptionLabelsWhenDistributionOptionsAreMissing() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-command-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    ExtensionInstallCommand installCommand = new ExtensionInstallCommand(userHome.toString());
    ExtensionCommand extensionCommand = new ExtensionCommand(installCommand);
    String[] args = { "install", extensionJarPath.toString() };

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      CommandLine commandLine = new CommandLine(extensionCommand.spec());
      int exitCode = commandLine.execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(outputCaptor.getStandardError()).contains("'--distribution-id=<id*>'").contains("'--distribution-version=<version*>'");
    }
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
