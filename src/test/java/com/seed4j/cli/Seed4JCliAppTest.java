package com.seed4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.bootstrap.application.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.application.PreSpringRuntimeEnvironmentProvider;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringLauncherAssembler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliAppTest {

  @Test
  void shouldForwardArgumentsWhenRunningProductionPath() {
    RecordingBootstrapExitCodeResolver bootstrapExitCodeResolver = new RecordingBootstrapExitCodeResolver();

    Seed4JCliApp.productionExitCode(new String[] { "--version" }, bootstrapExitCodeResolver);

    assertThat(bootstrapExitCodeResolver.arguments()).containsExactly("--version");
  }

  @Test
  void shouldExitWithCodeReturnedByProductionBootstrapWhenRunningProductionPath() {
    RecordingBootstrapExitCodeResolver bootstrapExitCodeResolver = new RecordingBootstrapExitCodeResolver(79);

    int exitCode = Seed4JCliApp.productionExitCode(new String[] { "--version" }, bootstrapExitCodeResolver);

    assertThat(exitCode).isEqualTo(79);
  }

  @Test
  void shouldRunTheProductionBootstrapEntryPointUsingTheLauncherPath() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      userHome,
      Path.of("seed4j-cli.jar"),
      "2.2.0",
      true,
      Path.of(System.getProperty("java.home"), "bin", "java")
    );
    RecordingPreSpringRuntimeEnvironmentProvider preSpringRuntimeEnvironmentProvider = new RecordingPreSpringRuntimeEnvironmentProvider(
      runtimeEnvironment
    );

    int exitCode = Seed4JCliApp.productionExitCode(
      new String[] { "--version" },
      Seed4JCliApp.productionBootstrapExitCodeResolver(preSpringRuntimeEnvironmentProvider, new PreSpringLauncherAssembler())
    );

    assertThat(exitCode).isZero();
    assertThat(preSpringRuntimeEnvironmentProvider.currentCalls()).isEqualTo(1);
  }

  @Test
  void shouldBuildProductionBootstrapExitCodeResolverUsingCurrentRuntimeEnvironmentProvider() {
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      Path.of("/home/user"),
      Path.of("/tmp/seed4j-cli.jar"),
      "2.2.0",
      true,
      Path.of("/tmp/jdk/bin/java")
    );
    RecordingPreSpringRuntimeEnvironmentProvider preSpringRuntimeEnvironmentProvider = new RecordingPreSpringRuntimeEnvironmentProvider(
      runtimeEnvironment
    );
    RecordingPreSpringLauncherAssembler preSpringLauncherAssembler = new RecordingPreSpringLauncherAssembler(61);

    Seed4JCliApp.BootstrapExitCodeResolver bootstrapExitCodeResolver = Seed4JCliApp.productionBootstrapExitCodeResolver(
      preSpringRuntimeEnvironmentProvider,
      preSpringLauncherAssembler
    );
    int exitCode = bootstrapExitCodeResolver.exitCodeFor(new String[] { "--version" });

    assertThat(exitCode).isEqualTo(61);
    assertThat(preSpringRuntimeEnvironmentProvider.currentCalls()).isEqualTo(1);
    assertThat(preSpringLauncherAssembler.runtimeEnvironment()).isEqualTo(runtimeEnvironment);
    assertThat(preSpringLauncherAssembler.arguments()).containsExactly("--version");
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

  private static final class RecordingPreSpringRuntimeEnvironmentProvider implements PreSpringRuntimeEnvironmentProvider {

    private final PreSpringRuntimeEnvironment runtimeEnvironment;
    private int currentCalls;

    private RecordingPreSpringRuntimeEnvironmentProvider(PreSpringRuntimeEnvironment runtimeEnvironment) {
      this.runtimeEnvironment = runtimeEnvironment;
    }

    @Override
    public PreSpringRuntimeEnvironment current() {
      currentCalls++;
      return runtimeEnvironment;
    }

    int currentCalls() {
      return currentCalls;
    }
  }

  private static final class RecordingPreSpringLauncherAssembler extends PreSpringLauncherAssembler {

    private final int exitCode;
    private PreSpringRuntimeEnvironment runtimeEnvironment;
    private String[] arguments;

    private RecordingPreSpringLauncherAssembler(int exitCode) {
      this.exitCode = exitCode;
    }

    @Override
    public int exitCodeFor(PreSpringRuntimeEnvironment runtimeEnvironment, String[] args) {
      this.runtimeEnvironment = runtimeEnvironment;
      this.arguments = args;
      return exitCode;
    }

    PreSpringRuntimeEnvironment runtimeEnvironment() {
      return runtimeEnvironment;
    }

    String[] arguments() {
      return arguments;
    }
  }
}
