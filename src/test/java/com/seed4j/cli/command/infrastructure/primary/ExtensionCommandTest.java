package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.application.RuntimeExtensionInstallApplicationService;
import com.seed4j.cli.command.application.RuntimeExtensionModeApplicationService;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallationException;
import com.seed4j.cli.command.domain.RuntimeExtensionInstaller;
import com.seed4j.cli.command.domain.RuntimeExtensionModeSwitchException;
import com.seed4j.cli.command.domain.RuntimeExtensionModeSwitchResult;
import com.seed4j.cli.command.domain.RuntimeExtensionModeSwitcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

@UnitTest
class ExtensionCommandTest {

  private static final String DISTRIBUTION_ID = "company-extension";
  private static final String DISTRIBUTION_VERSION = "1.0.0";

  @Test
  void shouldListExtensionSubcommandsWhenShowingExtensionHelp() {
    Path userHome = Path.of(System.getProperty("user.home"));
    ExtensionCommand extensionCommand = extensionCommand(userHome);

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      CommandLine commandLine = new CommandLine(extensionCommand.spec());
      int exitCode = commandLine.execute("--help");

      assertThat(exitCode).isZero();
      assertThat(outputCaptor.getStandardOutput()).contains("install").contains("enable").contains("disable");
    }
  }

  @Nested
  @DisplayName("install")
  class Install {

    @Test
    void shouldInstallExtensionRuntimeAndPrintRuntimePathsAndValidationHintsWhenInputsAreValid() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-command-");
      Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
      ExtensionCommand extensionCommand = extensionCommand(userHome);
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
    void shouldWarnRuntimeReplacementWhenInstallApplicationReportsReplacement() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-command-");
      Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
      ExtensionInstallCommand installCommand = installCommand(installationResult(userHome, true));
      ExtensionCommand extensionCommand = extensionCommand(installCommand, userHome);
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
      Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
      ExtensionInstallCommand installCommand = installCommandThrowing("Could not read ~/.config/seed4j-cli/config.yml. Details: broken");
      ExtensionCommand extensionCommand = extensionCommand(installCommand, userHome);
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
      ExtensionCommand extensionCommand = extensionCommand(userHome);
      String[] args = { "install", extensionJarPath.toString() };

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        CommandLine commandLine = new CommandLine(extensionCommand.spec());
        int exitCode = commandLine.execute(args);

        assertThat(exitCode).isEqualTo(2);
        assertThat(outputCaptor.getStandardError()).contains("'--distribution-id=<id*>'").contains("'--distribution-version=<version*>'");
      }
    }

    @Test
    void shouldSendRuntimeExtensionCoordinatesToInstallApplicationWhenInstallCommandSucceeds() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-command-");
      Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
      CapturingRuntimeExtensionInstaller runtimeExtensionInstaller = new CapturingRuntimeExtensionInstaller(
        installationResult(userHome, false)
      );
      ExtensionInstallCommand installCommand = new ExtensionInstallCommand(
        new RuntimeExtensionInstallApplicationService(runtimeExtensionInstaller)
      );
      ExtensionCommand extensionCommand = extensionCommand(installCommand, userHome);
      String[] args = {
        "install",
        extensionJarPath.toString(),
        "--distribution-id",
        DISTRIBUTION_ID,
        "--distribution-version",
        DISTRIBUTION_VERSION,
      };

      int exitCode;

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        CommandLine commandLine = new CommandLine(extensionCommand.spec());
        exitCode = commandLine.execute(args);

        assertThat(outputCaptor.getStandardOutput()).contains("Extension runtime installed successfully.");
      }

      assertThat(exitCode).isZero();
      assertThat(runtimeExtensionInstaller.request.extensionJarPath()).isEqualTo(extensionJarPath.toString());
      assertThat(runtimeExtensionInstaller.request.distributionId()).isEqualTo(DISTRIBUTION_ID);
      assertThat(runtimeExtensionInstaller.request.distributionVersion()).isEqualTo(DISTRIBUTION_VERSION);
    }
  }

  @Nested
  @DisplayName("enable")
  class Enable {

    @Test
    void shouldEnableExtensionRuntimeAndPrintConfigPath() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-extension-enable-command-");
      RuntimeExtensionModeSwitcherStub runtimeExtensionModeSwitcher = runtimeExtensionModeSwitcher(userHome);
      ExtensionCommand extensionCommand = extensionCommand(installCommand(userHome), runtimeExtensionModeSwitcher);
      String[] args = { "enable" };

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        CommandLine commandLine = new CommandLine(extensionCommand.spec());
        int exitCode = commandLine.execute(args);

        assertThat(exitCode).isZero();
        assertThat(outputCaptor.getStandardOutput())
          .contains("Extension runtime enabled successfully.")
          .contains("Config: " + userHome.resolve(".config/seed4j-cli/config.yml"));
      }

      assertThat(runtimeExtensionModeSwitcher.enableCalled).isTrue();
      assertThat(runtimeExtensionModeSwitcher.disableCalled).isFalse();
    }

    @Test
    void shouldReturnNonZeroAndShowObjectiveErrorWhenEnableFails() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-extension-enable-command-");
      RuntimeExtensionModeSwitcherStub runtimeExtensionModeSwitcher = runtimeExtensionModeSwitcher(userHome);
      runtimeExtensionModeSwitcher.enableException = new RuntimeExtensionModeSwitchException("Invalid runtime metadata file", null);
      ExtensionCommand extensionCommand = extensionCommand(installCommand(userHome), runtimeExtensionModeSwitcher);
      String[] args = { "enable" };

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        CommandLine commandLine = new CommandLine(extensionCommand.spec());
        int exitCode = commandLine.execute(args);

        assertThat(exitCode).isNotZero();
        assertThat(outputCaptor.getStandardError()).contains("Invalid runtime metadata file");
        assertThat(outputCaptor.getStandardOutput()).doesNotContain("Extension runtime enabled successfully.");
      }

      assertThat(runtimeExtensionModeSwitcher.enableCalled).isTrue();
      assertThat(runtimeExtensionModeSwitcher.disableCalled).isFalse();
    }
  }

  @Nested
  @DisplayName("disable")
  class Disable {

    @Test
    void shouldDisableExtensionRuntimeAndPrintConfigPath() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-extension-disable-command-");
      RuntimeExtensionModeSwitcherStub runtimeExtensionModeSwitcher = runtimeExtensionModeSwitcher(userHome);
      ExtensionCommand extensionCommand = extensionCommand(installCommand(userHome), runtimeExtensionModeSwitcher);
      String[] args = { "disable" };

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        CommandLine commandLine = new CommandLine(extensionCommand.spec());
        int exitCode = commandLine.execute(args);

        assertThat(exitCode).isZero();
        assertThat(outputCaptor.getStandardOutput())
          .contains("Extension runtime disabled successfully.")
          .contains("Config: " + userHome.resolve(".config/seed4j-cli/config.yml"));
      }

      assertThat(runtimeExtensionModeSwitcher.enableCalled).isFalse();
      assertThat(runtimeExtensionModeSwitcher.disableCalled).isTrue();
    }

    @Test
    void shouldReturnNonZeroAndShowObjectiveErrorWhenDisableFails() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-extension-disable-command-");
      RuntimeExtensionModeSwitcherStub runtimeExtensionModeSwitcher = runtimeExtensionModeSwitcher(userHome);
      runtimeExtensionModeSwitcher.disableException = new RuntimeExtensionModeSwitchException(
        "Could not read ~/.config/seed4j-cli/config.yml. Details: broken",
        null
      );
      ExtensionCommand extensionCommand = extensionCommand(installCommand(userHome), runtimeExtensionModeSwitcher);
      String[] args = { "disable" };

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        CommandLine commandLine = new CommandLine(extensionCommand.spec());
        int exitCode = commandLine.execute(args);

        assertThat(exitCode).isNotZero();
        assertThat(outputCaptor.getStandardError()).contains("Could not read ~/.config/seed4j-cli/config.yml.").contains("Details:");
        assertThat(outputCaptor.getStandardOutput()).doesNotContain("Extension runtime disabled successfully.");
      }

      assertThat(runtimeExtensionModeSwitcher.enableCalled).isFalse();
      assertThat(runtimeExtensionModeSwitcher.disableCalled).isTrue();
    }
  }

  private static ExtensionInstallCommand installCommand(Path userHome) {
    return installCommand(installationResult(userHome, false));
  }

  private static ExtensionCommand extensionCommand(Path userHome) {
    return extensionCommand(installCommand(userHome), userHome);
  }

  private static ExtensionCommand extensionCommand(ExtensionInstallCommand installCommand, Path userHome) {
    return extensionCommand(installCommand, runtimeExtensionModeSwitcher(userHome));
  }

  private static ExtensionCommand extensionCommand(
    ExtensionInstallCommand installCommand,
    RuntimeExtensionModeSwitcherStub runtimeExtensionModeSwitcher
  ) {
    RuntimeExtensionModeApplicationService modeApplicationService = new RuntimeExtensionModeApplicationService(
      runtimeExtensionModeSwitcher
    );

    return new ExtensionCommand(
      installCommand,
      new ExtensionEnableCommand(modeApplicationService),
      new ExtensionDisableCommand(modeApplicationService)
    );
  }

  private static ExtensionInstallCommand installCommand(RuntimeExtensionInstallResult installationResult) {
    return new ExtensionInstallCommand(new RuntimeExtensionInstallApplicationService(request -> installationResult));
  }

  private static ExtensionInstallCommand installCommandThrowing(String message) {
    return new ExtensionInstallCommand(
      new RuntimeExtensionInstallApplicationService(request -> {
        throw new RuntimeExtensionInstallationException(message, null);
      })
    );
  }

  private static RuntimeExtensionInstallResult installationResult(Path userHome, boolean runtimeReplaced) {
    return new RuntimeExtensionInstallResult(
      userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar"),
      userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml"),
      userHome.resolve(".config/seed4j-cli/config.yml"),
      runtimeReplaced
    );
  }

  private static RuntimeExtensionModeSwitcherStub runtimeExtensionModeSwitcher(Path userHome) {
    return new RuntimeExtensionModeSwitcherStub(userHome.resolve(".config/seed4j-cli/config.yml"));
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

  private static final class CapturingRuntimeExtensionInstaller implements RuntimeExtensionInstaller {

    private final RuntimeExtensionInstallResult result;
    private RuntimeExtensionInstallRequest request;

    private CapturingRuntimeExtensionInstaller(RuntimeExtensionInstallResult result) {
      this.result = result;
    }

    @Override
    public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
      this.request = request;

      return result;
    }
  }

  private static final class RuntimeExtensionModeSwitcherStub implements RuntimeExtensionModeSwitcher {

    private final Path configPath;
    private RuntimeExtensionModeSwitchException enableException;
    private RuntimeExtensionModeSwitchException disableException;
    private boolean enableCalled;
    private boolean disableCalled;

    private RuntimeExtensionModeSwitcherStub(Path configPath) {
      this.configPath = configPath;
    }

    @Override
    public RuntimeExtensionModeSwitchResult enable() {
      enableCalled = true;
      if (enableException != null) {
        throw enableException;
      }

      return new RuntimeExtensionModeSwitchResult(configPath);
    }

    @Override
    public RuntimeExtensionModeSwitchResult disable() {
      disableCalled = true;
      if (disableException != null) {
        throw disableException;
      }

      return new RuntimeExtensionModeSwitchResult(configPath);
    }
  }
}
