package com.seed4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeChangePlan;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncher;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncherFactory;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRunner;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliAppTest {

  @Test
  void shouldForwardArgumentsWhenRunningProductionPath() {
    RecordingBootstrapExitCodeResolver bootstrapExitCodeResolver = new RecordingBootstrapExitCodeResolver();

    bootstrapExitCodeResolver.exitCodeFor(new String[] { "--version" });

    assertThat(bootstrapExitCodeResolver.arguments()).containsExactly("--version");
  }

  @Test
  void shouldExitWithCodeReturnedByProductionBootstrapWhenRunningProductionPath() {
    RecordingBootstrapExitCodeResolver bootstrapExitCodeResolver = new RecordingBootstrapExitCodeResolver(79);

    int exitCode = bootstrapExitCodeResolver.exitCodeFor(new String[] { "--version" });

    assertThat(exitCode).isEqualTo(79);
  }

  @Test
  void shouldBuildProductionBootstrapExitCodeResolverUsingPreWiredPrimaryAdapter() {
    RecordingPreSpringBootstrapRunner preSpringBootstrapRunner = new RecordingPreSpringBootstrapRunner(61);

    Seed4JCliApp.BootstrapExitCodeResolver bootstrapExitCodeResolver = preSpringBootstrapRunner::exitCodeFor;
    int exitCode = bootstrapExitCodeResolver.exitCodeFor(new String[] { "--version" });

    assertThat(exitCode).isEqualTo(61);
    assertThat(preSpringBootstrapRunner.arguments()).containsExactly("--version");
  }

  @Test
  void shouldBuildProductionBootstrapExitCodeResolverUsingConfigurationWiring() {
    Seed4JCliApp.BootstrapExitCodeResolver bootstrapExitCodeResolver = Seed4JCliApp.productionBootstrapExitCodeResolver();

    assertThat(bootstrapExitCodeResolver).isNotNull();
  }

  private static final class RecordingBootstrapExitCodeResolver implements Seed4JCliApp.BootstrapExitCodeResolver {

    private final int exitCode;
    private String[] arguments;

    private RecordingBootstrapExitCodeResolver() {
      this(23);
    }

    private RecordingBootstrapExitCodeResolver(int exitCode) {
      this.exitCode = exitCode;
    }

    @Override
    public int exitCodeFor(String[] args) {
      this.arguments = args;
      return exitCode;
    }

    String[] arguments() {
      return arguments;
    }
  }

  private static final class RecordingPreSpringBootstrapRunner extends PreSpringBootstrapRunner {

    private final int exitCode;
    private String[] arguments;

    private RecordingPreSpringBootstrapRunner(int exitCode) {
      super(new PreSpringBootstrapApplicationService(seed4jCliLauncher()));
      this.exitCode = exitCode;
    }

    @Override
    public int exitCodeFor(String[] args) {
      this.arguments = args;
      return exitCode;
    }

    String[] arguments() {
      return arguments;
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
    return new Seed4JCliLauncherFactory().create(runtimeEnvironment, runtimeModeConfigurationRepository, launcherDependencies);
  }
}
