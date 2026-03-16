package com.seed4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.Seed4JCliApp.BootstrapEntryPoint;
import com.seed4j.cli.Seed4JCliApp.ProductionBootstrapEntryPointFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliAppTest {

  @Test
  void shouldForwardArgumentsWhenRunningProductionPath() {
    RecordingBootstrapEntryPoint bootstrapEntryPoint = new RecordingBootstrapEntryPoint();
    RecordingBootstrapEntryPointFactory bootstrapEntryPointFactory = new RecordingBootstrapEntryPointFactory(bootstrapEntryPoint);
    RecordingExitHandler exitHandler = new RecordingExitHandler();

    Seed4JCliApp.runProductionPath(new String[] { "--version" }, bootstrapEntryPointFactory, exitHandler);

    assertThat(bootstrapEntryPoint.arguments()).containsExactly("--version");
  }

  @Test
  void shouldExitWithCodeReturnedByProductionBootstrapWhenRunningProductionPath() {
    RecordingBootstrapEntryPoint bootstrapEntryPoint = new RecordingBootstrapEntryPoint(79);
    RecordingBootstrapEntryPointFactory bootstrapEntryPointFactory = new RecordingBootstrapEntryPointFactory(bootstrapEntryPoint);
    RecordingExitHandler exitHandler = new RecordingExitHandler();

    Seed4JCliApp.runProductionPath(new String[] { "--version" }, bootstrapEntryPointFactory, exitHandler);

    assertThat(exitHandler.exitCode()).isEqualTo(79);
  }

  @Test
  void shouldRunTheProductionBootstrapEntryPointUsingTheLauncherPath() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
    RecordingExitHandler exitHandler = new RecordingExitHandler();

    Seed4JCliApp.runProductionPath(
      new String[] { "--version" },
      () -> Seed4JCliApp.productionBootstrapEntryPoint(userHome, true),
      exitHandler
    );

    assertThat(exitHandler.exitCode()).isZero();
  }

  private static final class RecordingBootstrapEntryPoint implements Seed4JCliApp.BootstrapEntryPoint {

    private final int exitCode;
    private String[] arguments;

    private RecordingBootstrapEntryPoint() {
      this(23);
    }

    private RecordingBootstrapEntryPoint(int exitCode) {
      this.exitCode = exitCode;
    }

    @Override
    public int launch(String[] args) {
      this.arguments = args;
      return exitCode;
    }

    String[] arguments() {
      return arguments;
    }
  }

  private record RecordingBootstrapEntryPointFactory(
    BootstrapEntryPoint bootstrapEntryPoint
  ) implements ProductionBootstrapEntryPointFactory {
    @Override
    public BootstrapEntryPoint create() {
      return bootstrapEntryPoint;
    }
  }

  private static final class RecordingExitHandler implements Seed4JCliApp.ExitHandler {

    private Integer exitCode;

    @Override
    public void exit(int exitCode) {
      this.exitCode = exitCode;
    }

    Integer exitCode() {
      return exitCode;
    }
  }
}
