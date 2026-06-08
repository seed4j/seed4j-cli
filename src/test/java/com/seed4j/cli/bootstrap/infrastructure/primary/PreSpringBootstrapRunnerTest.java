package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeChangePlan;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliRuntime;
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
    assertThat(preSpringBootstrapApplicationService.receivedArguments().values()).containsExactly("--version");
  }

  private static final class RecordingPreSpringBootstrapApplicationService extends PreSpringBootstrapApplicationService {

    private final int exitCode;
    private Seed4JCliArguments receivedArguments;

    private RecordingPreSpringBootstrapApplicationService(int exitCode) {
      super(
        seed4jCliRuntime(),
        runtimeModeConfigurationRepository(),
        () -> RuntimeSelection.standard(),
        command -> {
          throw new IllegalStateException("Should not execute a child process in this test.");
        },
        args -> {
          throw new IllegalStateException("Should not run the local CLI in this test.");
        },
        executablePath -> true,
        () -> {},
        new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
      );
      this.exitCode = exitCode;
    }

    @Override
    public int exitCodeFor(Seed4JCliArguments arguments) {
      this.receivedArguments = arguments;
      return exitCode;
    }

    private Seed4JCliArguments receivedArguments() {
      return receivedArguments;
    }
  }

  private static RuntimeModeConfigurationRepository runtimeModeConfigurationRepository() {
    return new RuntimeModeConfigurationRepository() {
      @Override
      public RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
        throw new UnsupportedOperationException("Not required in this test.");
      }

      @Override
      public RuntimeMode readMode() {
        return RuntimeMode.STANDARD;
      }
    };
  }

  private static Seed4JCliRuntime seed4jCliRuntime() {
    return new Seed4JCliRuntime() {
      @Override
      public Path executableJar() {
        return Path.of("/tmp/seed4j-cli.jar");
      }

      @Override
      public boolean childRuntime() {
        return true;
      }
    };
  }
}
