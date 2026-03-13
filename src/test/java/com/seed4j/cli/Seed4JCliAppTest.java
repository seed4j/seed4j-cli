package com.seed4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliAppTest {

  @Test
  void shouldDelegateStartupToTheLauncher() {
    RecordingLauncher launcher = new RecordingLauncher();

    int exitCode = Seed4JCliApp.run(new String[] { "--version" }, launcher);

    assertThat(exitCode).isEqualTo(23);
    assertThat(launcher.arguments()).containsExactly("--version");
  }

  private static final class RecordingLauncher implements Seed4JCliApp.EntryPointLauncher {

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

  /*
  [TEST] Main exits with the code returned by the launcher-backed run path
  */
}
