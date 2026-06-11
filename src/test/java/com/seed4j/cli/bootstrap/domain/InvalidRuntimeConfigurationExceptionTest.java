package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import org.junit.jupiter.api.Test;

@UnitTest
class InvalidRuntimeConfigurationExceptionTest {

  @Test
  void shouldPreserveCauseAndAppendCauseMessageWhenCreatingTechnicalError() {
    IOException technicalCause = new IOException("cannot persist");

    InvalidRuntimeConfigurationException technicalError = InvalidRuntimeConfigurationException.technicalError(
      "Could not update ~/.config/seed4j-cli/config.yml.",
      technicalCause
    );

    assertThat(technicalError)
      .hasMessage("Could not update ~/.config/seed4j-cli/config.yml. Details: cannot persist")
      .hasCause(technicalCause);
  }

  @Test
  void shouldUseCauseTypeWhenCauseMessageIsBlank() {
    IOException technicalCause = new IOException("   ");

    InvalidRuntimeConfigurationException technicalError = InvalidRuntimeConfigurationException.technicalError(
      "Could not launch child process.",
      technicalCause
    );

    assertThat(technicalError).hasMessage("Could not launch child process. Details: IOException").hasCause(technicalCause);
  }

  @Test
  void shouldKeepBaseMessageWhenCauseIsMissing() {
    InvalidRuntimeConfigurationException technicalError = InvalidRuntimeConfigurationException.technicalError(
      "Could not resolve executable path.",
      null
    );

    assertThat(technicalError).hasMessage("Could not resolve executable path.").hasNoCause();
  }

  @Test
  void shouldSkipTechnicalDetailsWhenFallbackDetailIsBlank() {
    RuntimeException technicalCause = new RuntimeException(" ") {};

    InvalidRuntimeConfigurationException technicalError = InvalidRuntimeConfigurationException.technicalError(
      "Could not inspect runtime metadata.",
      technicalCause
    );

    assertThat(technicalError).hasMessage("Could not inspect runtime metadata.").hasCause(technicalCause);
  }
}
