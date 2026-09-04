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
class ExtensionEnableCommandTest extends ExtensionRuntimeCommandsTest {

  @Test
  void shouldEnableExtensionRuntime(CapturedOutput output) throws IOException {
    ExtensionRuntimePaths runtimePaths = runtimePaths();
    writeRuntimeMode(runtimePaths.configPath(), "standard");
    installActiveRuntime(runtimePaths);

    int exitCode = commandLine().execute("extension", "enable");

    assertThat(exitCode).isZero();
    assertThat(output.getOut())
      .contains("Extension runtime enabled successfully.")
      .contains("Config: " + runtimePaths.configPath());
    assertThat(Files.readString(runtimePaths.configPath())).contains("mode: extension");
  }

  @Test
  void shouldReturnNonZeroAndNotChangeConfigWhenEnablingInvalidExtensionRuntime(CapturedOutput output) throws IOException {
    ExtensionRuntimePaths runtimePaths = runtimePaths();
    String originalConfig = writeRuntimeMode(runtimePaths.configPath(), "standard");
    installRuntimeWithoutMetadata(runtimePaths);

    int exitCode = commandLine().execute("extension", "enable");

    assertThat(exitCode).isNotZero();
    assertThat(output.getErr()).contains("Invalid runtime metadata file");
    assertThat(output.getOut()).doesNotContain("Extension runtime enabled successfully.");
    assertThat(Files.readString(runtimePaths.configPath())).isEqualTo(originalConfig);
  }
}
