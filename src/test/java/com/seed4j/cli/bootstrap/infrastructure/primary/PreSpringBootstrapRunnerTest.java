package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
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
      super(seed4jCliLauncher());
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

  private static Seed4JCliLauncher seed4jCliLauncher() {
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
      args -> {
        throw new IllegalStateException("Should not run the local CLI in this test.");
      }
    );
    return new Seed4JCliLauncherFactory().create(
      runtimeEnvironment,
      runtimeModeConfigurationRepository,
      () -> com.seed4j.cli.bootstrap.domain.RuntimeSelection.standard(),
      launcherDependencies
    );
  }
}
