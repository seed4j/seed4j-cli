package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapCommand;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringBootstrapRunnerTest {

  @Test
  void shouldDelegateArgsToApplicationServiceAndReturnItsExitCode() {
    RecordingPreSpringBootstrapApplicationService preSpringBootstrapApplicationService = new RecordingPreSpringBootstrapApplicationService(
      53
    );
    PreSpringBootstrapRunner runner = new PreSpringBootstrapRunner(preSpringBootstrapApplicationService);

    int exitCode = runner.exitCodeFor(new String[] { "--version" });

    assertThat(exitCode).isEqualTo(53);
    assertThat(preSpringBootstrapApplicationService.receivedCommand().args()).containsExactly("--version");
  }

  private static final class RecordingPreSpringBootstrapApplicationService extends PreSpringBootstrapApplicationService {

    private final int exitCode;
    private PreSpringBootstrapCommand receivedCommand;

    private RecordingPreSpringBootstrapApplicationService(int exitCode) {
      super(
        (userHomePath, executablePath, currentSeed4JVersion, javaExecutablePath) -> (args, childMode) -> exitCode,
        () ->
          new PreSpringRuntimeEnvironment(
            Path.of("/home/user"),
            Path.of("/tmp/seed4j-cli.jar"),
            "2.2.0",
            false,
            Path.of("/tmp/jdk/bin/java")
          )
      );
      this.exitCode = exitCode;
    }

    @Override
    public int exitCodeFor(PreSpringBootstrapCommand command) {
      this.receivedCommand = command;
      return exitCode;
    }

    private PreSpringBootstrapCommand receivedCommand() {
      return receivedCommand;
    }
  }
}
