package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import picocli.CommandLine;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest(
  properties = {
    "seed4j.cli.runtime.mode=extension",
    "seed4j.cli.runtime.distribution.id=company-extension",
    "seed4j.cli.runtime.distribution.version=1.0.0",
  }
)
class Seed4JCommandsExtensionRuntimeSpringContextTest {

  private static final String DISTRIBUTION_ID = "company-extension";
  private static final String DISTRIBUTION_VERSION = "1.0.0";

  @Autowired
  private Seed4JCommandsFactory commandsFactory;

  @Test
  void shouldShowExtensionRuntimeDistributionInVersionOutputUsingSpringManagedCommandGraph(CapturedOutput output) {
    String[] args = { "--version" };

    int exitCode = new CommandLine(commandsFactory.buildCommandSpec()).execute(args);

    assertThat(exitCode).isZero();
    assertThat(output.getOut())
      .contains("Runtime mode: extension")
      .contains("Distribution ID: " + DISTRIBUTION_ID)
      .contains("Distribution version: " + DISTRIBUTION_VERSION);
  }
}
