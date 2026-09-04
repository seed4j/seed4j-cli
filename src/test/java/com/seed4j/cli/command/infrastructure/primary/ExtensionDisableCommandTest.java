package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
class ExtensionDisableCommandTest extends ExtensionRuntimeCommandsTest {

  @Test
  void shouldDisableExtensionRuntimeAndPreserveArtifacts(CapturedOutput output) throws IOException {
    ExtensionRuntimePaths runtimePaths = runtimePaths();
    ActiveRuntimeArtifacts activeRuntimeArtifacts = installActiveRuntime(runtimePaths);

    int exitCode = commandLine().execute("extension", "disable");

    assertThat(exitCode).isZero();
    assertThat(output.getOut())
      .contains("Extension runtime disabled successfully.")
      .contains("Config: " + runtimePaths.configPath());
    assertThat(Files.readString(runtimePaths.configPath())).contains("mode: standard");
    assertThat(Files.readAllBytes(runtimePaths.runtimeJarPath())).isEqualTo(activeRuntimeArtifacts.jarContent());
    assertThat(Files.readString(runtimePaths.metadataPath())).isEqualTo(activeRuntimeArtifacts.metadataContent());
  }

  @Test
  void shouldReturnNonZeroAndPreserveInvalidConfigWhenDisabling(CapturedOutput output) throws IOException {
    ExtensionRuntimePaths runtimePaths = runtimePaths();
    Files.createDirectories(runtimePaths.configPath().getParent());
    Files.writeString(runtimePaths.configPath(), "seed4j: [broken");

    int exitCode = commandLine().execute("extension", "disable");

    assertThat(exitCode).isNotZero();
    assertThat(output.getErr()).contains("Could not read ~/.config/seed4j-cli/config.yml.").contains("Details:");
    assertThat(output.getOut()).doesNotContain("Extension runtime disabled successfully.");
    assertThat(Files.readString(runtimePaths.configPath())).isEqualTo("seed4j: [broken");
  }
}
