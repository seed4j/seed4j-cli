package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import picocli.CommandLine;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
class ExtensionRuntimeCommandsTest {

  @Autowired
  private Seed4JCommandsFactory commandsFactory;

  @Test
  void shouldExplainTheTransitionInExtensionHelp(CapturedOutput output) {
    CommandLine cli = new CommandLine(commandsFactory.buildCommandSpec());

    int exitCode = cli.execute("extension", "--help");

    assertThat(exitCode).isZero();
    assertThat(output.getOut()).contains("Loading runtime extension JARs is no longer supported.", "install", "enable", "disable");
  }

  @ParameterizedTest
  @MethodSource("formerInvocations")
  void shouldRejectFormerManagementCommands(String[] arguments, CapturedOutput output) {
    CommandLine cli = new CommandLine(commandsFactory.buildCommandSpec());

    int exitCode = cli.execute(arguments);

    assertThat(exitCode).isEqualTo(2);
    assertThat(output.getErr()).contains("Loading runtime extension JARs is no longer supported.");
  }

  static Stream<Arguments> formerInvocations() {
    return Stream.of(
      Arguments.of(
        (Object) new String[] {
          "extension",
          "install",
          "company-extension.jar",
          "--distribution-id",
          "company",
          "--distribution-version",
          "1.0.0",
        }
      ),
      Arguments.of((Object) new String[] { "extension", "install" }),
      Arguments.of((Object) new String[] { "extension", "enable" }),
      Arguments.of((Object) new String[] { "extension", "disable" })
    );
  }
}
