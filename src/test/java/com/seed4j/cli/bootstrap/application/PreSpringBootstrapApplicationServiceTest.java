package com.seed4j.cli.bootstrap.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeChangePlan;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncher;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncherFactory;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringBootstrapApplicationServiceTest {

  @Test
  void shouldDelegateArgsToLauncherAndReturnItsExitCode() {
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner(37);
    Seed4JCliLauncher seed4jCliLauncher = seed4jCliLauncher(localCliRunner);
    PreSpringBootstrapApplicationService service = new PreSpringBootstrapApplicationService(seed4jCliLauncher);
    Seed4JCliArguments arguments = new Seed4JCliArguments(new String[] { "--version" });

    int exitCode = service.exitCodeFor(arguments);

    assertThat(exitCode).isEqualTo(37);
    assertThat(localCliRunner.arguments()).isEqualTo(arguments);
  }

  private static Seed4JCliLauncher seed4jCliLauncher(LocalCliRunner localCliRunner) {
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      new Seed4JCliHome(Path.of("/home/user")),
      Path.of("/tmp/seed4j-cli.jar"),
      true,
      Path.of("/tmp/jdk/bin/java")
    );
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository = new RuntimeModeConfigurationRepository() {
      @Override
      public RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
        throw new UnsupportedOperationException("Not required in this test.");
      }

      @Override
      public RuntimeMode readMode() {
        return RuntimeMode.STANDARD;
      }
    };
    Seed4JCliLauncherFactory.LauncherDependencies launcherDependencies = new Seed4JCliLauncherFactory.LauncherDependencies(
      command -> {
        throw new IllegalStateException("Should not execute a child process in this test.");
      },
      localCliRunner
    );
    return new Seed4JCliLauncherFactory().create(
      runtimeEnvironment,
      runtimeModeConfigurationRepository,
      () -> com.seed4j.cli.bootstrap.domain.RuntimeSelection.standard(),
      launcherDependencies
    );
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
