package com.seed4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliAppTest {

  @Test
  void shouldForwardArgumentsToTheBootstrapEntryPoint() {
    RecordingBootstrapEntryPoint bootstrapEntryPoint = new RecordingBootstrapEntryPoint();
    RecordingExitHandler exitHandler = new RecordingExitHandler();

    Seed4JCliApp.main(new String[] { "--version" }, bootstrapEntryPoint, exitHandler);

    assertThat(bootstrapEntryPoint.arguments()).containsExactly("--version");
  }

  /*
  [TEST] Seed4JCliApp exits with the code returned by the bootstrap entrypoint
  [TEST] Seed4JCliApp no longer exposes factory-backed main overload paths
  */

  private static final class RecordingBootstrapEntryPoint implements Seed4JCliApp.BootstrapEntryPoint {

    private String[] arguments;

    @Override
    public int launch(String[] args) {
      this.arguments = args;
      return 23;
    }

    String[] arguments() {
      return arguments;
    }
  }

  private static final class RecordingExitHandler implements Seed4JCliApp.ExitHandler {

    private Integer exitCode;

    @Override
    public void exit(int exitCode) {
      this.exitCode = exitCode;
    }

    Integer exitCode() {
      return exitCode;
    }
  }
}
