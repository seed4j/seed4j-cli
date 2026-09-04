package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
class ExtensionRuntimeVersionCommandTest extends ExtensionRuntimeCommandsTest {

  @Test
  void shouldShowStandardRuntimeInVersionOutput(CapturedOutput output) {
    int exitCode = commandLine().execute("--version");

    assertThat(exitCode).isZero();
    assertThat(output.getOut()).contains("Runtime mode: standard").doesNotContain("Distribution ID").doesNotContain("Distribution version");
  }
}
