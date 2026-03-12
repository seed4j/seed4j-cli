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
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
  }

  @Test
  void shouldRunTheLocalCliPathWhenAlreadyExecutingAsAChildProcess() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" }, true);

    assertThat(exitCode).isEqualTo(12);
    assertThat(localCliRunner.wasCalled()).isTrue();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
  }

  @Test
  void shouldFailBeforeLaunchingAChildProcessWhenExtensionRuntimeConfigurationIsInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: extension
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isNotZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

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

  private static final class RecordingLocalCliRunner implements LocalCliRunner {

    private boolean called;

    @Override
    public int run(String[] args) {
      called = true;
      return 12;
    }

    boolean wasCalled() {
      return called;
    }
  }
}
