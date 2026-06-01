package com.seed4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
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
      super(
        new com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService(
          (userHomePath, executablePath, javaExecutablePath) -> (args, childMode) -> exitCode,
          () -> new PreSpringRuntimeEnvironment(Path.of("/home/user"), Path.of("/tmp/seed4j-cli.jar"), false, Path.of("/tmp/jdk/bin/java"))
        )
      );
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
}
