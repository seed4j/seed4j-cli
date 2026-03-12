package com.seed4j.cli.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliLauncherTest {

  @Test
  void shouldStartAStandardChildProcessWhenNoExternalRuntimeConfigExists() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(userHome, Path.of("/tmp/seed4j-cli.jar"), "0.0.1-SNAPSHOT", childProcessLauncher);

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
  }

  // [TEST] Runs the Spring path directly when already executing as a child process
  // [TEST] Fails before Spring startup when extension runtime configuration is invalid

  private static final class RecordingChildProcessLauncher implements ChildProcessLauncher {

    private RuntimeSelection runtimeSelection;

    @Override
    public int launch(RuntimeSelection runtimeSelection, String[] args) {
      this.runtimeSelection = runtimeSelection;
      return 0;
    }

    RuntimeSelection runtimeSelection() {
      return runtimeSelection;
    }
  }
}
