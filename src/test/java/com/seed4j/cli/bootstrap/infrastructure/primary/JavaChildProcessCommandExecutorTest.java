package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class JavaChildProcessCommandExecutorTest {

  @Test
  void shouldReturnZeroWhenChildProcessCommandSucceeds() {
    JavaChildProcessCommandExecutor commandExecutor = new JavaChildProcessCommandExecutor();
    List<String> command = List.of(defaultJavaExecutable().toString(), "-version");

    int exitCode = commandExecutor.execute(command);

    assertThat(exitCode).isZero();
  }

  @Test
  void shouldPropagateNonZeroExitCodeWhenChildProcessFails() {
    JavaChildProcessCommandExecutor commandExecutor = new JavaChildProcessCommandExecutor();
    List<String> command = List.of(defaultJavaExecutable().toString(), "--seed4j-invalid-option");

    int exitCode = commandExecutor.execute(command);

    assertThat(exitCode).isNotZero();
  }

  private static Path defaultJavaExecutable() {
    return Path.of(System.getProperty("java.home"), "bin", "java");
  }
}
