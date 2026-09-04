package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
class ExtensionInstallCommandTest extends ExtensionRuntimeCommandsTest {

  @Test
  void shouldInstallExtensionRuntime(CapturedOutput output) throws IOException {
    Path extensionJarPath = createFatJar(USER_HOME.resolve("company-extension.jar"));
    ExtensionRuntimePaths runtimePaths = runtimePaths();

    int exitCode = commandLine().execute(installArguments(extensionJarPath));

    assertThat(exitCode).isZero();
    assertThat(output.getOut())
      .contains("Extension runtime installed successfully.")
      .containsSubsequence("Validate installation with:", "  seed4j --version", "  seed4j list")
      .doesNotContain("Replaced active runtime extension.");
    assertThat(Files.readString(runtimePaths.configPath())).contains("mode: extension");
    assertThat(Files.readAllBytes(runtimePaths.runtimeJarPath())).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(runtimePaths.metadataPath()).exists();
    assertThat(Files.readString(runtimePaths.metadataPath()))
      .contains("id: " + DISTRIBUTION_ID)
      .contains("version: " + DISTRIBUTION_VERSION);
  }

  @Test
  void shouldReplaceActiveExtensionRuntime(CapturedOutput output) throws IOException {
    Path extensionJarPath = createFatJarWithClass(
      USER_HOME.resolve("company-extension.jar"),
      "BOOT-INF/classes/com/company/New.class",
      new byte[] { 2, 3 }
    );
    ExtensionRuntimePaths runtimePaths = runtimePaths();
    Files.createDirectories(runtimePaths.runtimeJarPath().getParent());
    createFatJarWithClass(runtimePaths.runtimeJarPath(), "BOOT-INF/classes/com/company/Legacy.class", new byte[] { 1 });
    writeRuntimeMetadata(runtimePaths.metadataPath(), "legacy-extension", "0.9.0");

    int exitCode = commandLine().execute(installArguments(extensionJarPath));

    assertThat(exitCode).isZero();
    assertThat(output.getOut()).contains("Replaced active runtime extension.").contains("Extension runtime installed successfully.");
    assertThat(Files.readAllBytes(runtimePaths.runtimeJarPath())).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(Files.readString(runtimePaths.metadataPath()))
      .contains("id: " + DISTRIBUTION_ID)
      .contains("version: " + DISTRIBUTION_VERSION);
  }

  @Test
  void shouldReturnNonZeroAndShowObjectiveErrorWhenRuntimeConfigIsInvalid(CapturedOutput output) throws IOException {
    Path extensionJarPath = createFatJar(USER_HOME.resolve("company-extension.jar"));
    ExtensionRuntimePaths runtimePaths = runtimePaths();
    writeRuntimeMode(runtimePaths.configPath(), "42");

    int exitCode = commandLine().execute(installArguments(extensionJarPath));

    assertThat(exitCode).isNotZero();
    assertThat(output.getErr()).contains("Invalid ~/.config/seed4j-cli/config.yml").contains("seed4j.runtime.mode must be a string");
    assertThat(output.getOut()).doesNotContain("Extension runtime installed successfully.");
  }
}
