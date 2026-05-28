package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class JavaChildProcessCommandExecutorTest {

  @Test
  void shouldReturnTheStartedProcessExitCodeWhenCommandExecutionSucceeds() {
    JavaChildProcessCommandExecutor commandExecutor = new JavaChildProcessCommandExecutor(command -> () -> 37);

    int exitCode = commandExecutor.execute(List.of("java", "-version"));

    assertThat(exitCode).isEqualTo(37);
  }

  @Test
  void shouldWrapTheIoExceptionWhenTheProcessCannotBeStarted() {
    IOException processStartException = new IOException("Permission denied");
    JavaChildProcessCommandExecutor commandExecutor = new JavaChildProcessCommandExecutor(command -> {
      throw processStartException;
    });

    assertThatThrownBy(() -> commandExecutor.execute(List.of("java", "-version")))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Could not launch child process. Details: Permission denied")
      .hasCause(processStartException);
  }

  @Test
  void shouldRestoreTheInterruptedFlagWhenProcessWaitIsInterrupted() {
    InterruptedException interruptedException = new InterruptedException("waiting interrupted");
    JavaChildProcessCommandExecutor commandExecutor = new JavaChildProcessCommandExecutor(
      command ->
        () -> {
          throw interruptedException;
        }
    );

    try {
      assertThatThrownBy(() -> commandExecutor.execute(List.of("java", "-version")))
        .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
        .hasMessage("Child process execution was interrupted. Details: waiting interrupted")
        .hasCause(interruptedException);

      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
