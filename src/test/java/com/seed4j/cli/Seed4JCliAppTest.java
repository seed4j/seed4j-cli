package com.seed4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliAppTest {

  @Test
  void shouldForwardArgumentsToTheBootstrapEntryPoint() {
    RecordingBootstrapEntryPoint bootstrapEntryPoint = new RecordingBootstrapEntryPoint();
    RecordingExitHandler exitHandler = new RecordingExitHandler();

    Seed4JCliApp.main(new String[] { "--version" }, bootstrapEntryPoint, exitHandler);

    assertThat(bootstrapEntryPoint.arguments()).containsExactly("--version");
  }

  @Test
  void shouldExitWithTheCodeReturnedByTheBootstrapEntrypoint() {
    RecordingBootstrapEntryPoint bootstrapEntryPoint = new RecordingBootstrapEntryPoint(41);
    RecordingExitHandler exitHandler = new RecordingExitHandler();

    Seed4JCliApp.main(new String[] { "--version" }, bootstrapEntryPoint, exitHandler);

    assertThat(exitHandler.exitCode()).isEqualTo(41);
  }

  @Test
  void shouldForwardArgumentsWhenUsingProductionBootstrapComposition() {
    RecordingBootstrapEntryPoint bootstrapEntryPoint = new RecordingBootstrapEntryPoint();
    RecordingBootstrapEntryPointFactory bootstrapEntryPointFactory = new RecordingBootstrapEntryPointFactory(bootstrapEntryPoint);
    RecordingExitHandler exitHandler = new RecordingExitHandler();

    Seed4JCliApp.main(new String[] { "--version" }, bootstrapEntryPointFactory, exitHandler);

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
  void shouldForwardArgumentsWhenRunningPublicMainPath() {
    RecordingBootstrapEntryPoint bootstrapEntryPoint = new RecordingBootstrapEntryPoint();
    RecordingBootstrapEntryPointFactory bootstrapEntryPointFactory = new RecordingBootstrapEntryPointFactory(bootstrapEntryPoint);
    RecordingExitHandler exitHandler = new RecordingExitHandler();

    Seed4JCliApp.runPublicMain(new String[] { "--version" }, bootstrapEntryPointFactory, exitHandler);

    assertThat(bootstrapEntryPoint.arguments()).containsExactly("--version");
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

  private static final class RecordingBootstrapEntryPointFactory implements Seed4JCliApp.ProductionBootstrapEntryPointFactory {

    private final Seed4JCliApp.BootstrapEntryPoint bootstrapEntryPoint;

    private RecordingBootstrapEntryPointFactory(Seed4JCliApp.BootstrapEntryPoint bootstrapEntryPoint) {
      this.bootstrapEntryPoint = bootstrapEntryPoint;
    }

    @Override
    public Seed4JCliApp.BootstrapEntryPoint create() {
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
