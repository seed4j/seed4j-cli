package com.seed4j.cli.bootstrap.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.BootstrapOutput;
import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeChangePlan;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliRuntime;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringBootstrapApplicationServiceTest {

  @Test
  void shouldDelegateArgsToLauncherAndReturnItsExitCode() {
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner(37);
    PreSpringBootstrapApplicationService service = new PreSpringBootstrapApplicationService(
      new FixedSeed4JCliRuntime(),
      new StandardRuntimeModeConfigurationRepository(),
      () -> RuntimeSelection.standard(),
      command -> {
        throw new IllegalStateException("Should not execute a child process in this test.");
      },
      localCliRunner,
      executablePath -> true,
      () -> {},
      new SilentBootstrapOutput()
    );
    Seed4JCliArguments arguments = new Seed4JCliArguments(new String[] { "--version" });

    int exitCode = service.exitCodeFor(arguments);

    assertThat(exitCode).isEqualTo(37);
    assertThat(localCliRunner.arguments()).isEqualTo(arguments);
  }

  private static final class FixedSeed4JCliRuntime implements Seed4JCliRuntime {

    @Override
    public Path executableJar() {
      return Path.of("/tmp/seed4j-cli.jar");
    }

    @Override
    public boolean childRuntime() {
      return true;
    }
  }

  private static final class StandardRuntimeModeConfigurationRepository implements RuntimeModeConfigurationRepository {

    @Override
    public RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
      throw new UnsupportedOperationException("Not required in this test.");
    }

    @Override
    public RuntimeMode readMode() {
      return RuntimeMode.STANDARD;
    }
  }

  private static final class SilentBootstrapOutput implements BootstrapOutput {

    @Override
    public void standardModeFallback() {}

    @Override
    public void runtimeConfigurationError(String message) {}
  }

  private static final class RecordingLocalCliRunner implements LocalCliRunner {

    private final int exitCode;
    private Seed4JCliArguments arguments;

    private RecordingLocalCliRunner(int exitCode) {
      this.exitCode = exitCode;
    }

    @Override
    public int run(Seed4JCliArguments arguments) {
      this.arguments = arguments;
      return exitCode;
    }

    Seed4JCliArguments arguments() {
      return arguments;
    }
  }
}
