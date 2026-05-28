package com.seed4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.Seed4JCliApp.BootstrapEntryPoint;
import com.seed4j.cli.Seed4JCliApp.PreSpringBootstrapApplicationServiceFactory;
import com.seed4j.cli.Seed4JCliApp.ProductionBootstrapEntryPointFactory;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
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
    RecordingExitHandler exitHandler = new RecordingExitHandler();

    Seed4JCliApp.runProductionPath(
      new String[] { "--version" },
      () -> Seed4JCliApp.productionBootstrapEntryPoint(userHome, true),
      exitHandler
    );

    assertThat(exitHandler.exitCode()).isZero();
  }

  @Test
  void shouldDelegateProductionBootstrapEntryPointCreationToThePreSpringApplicationServiceFactory() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    RecordingPreSpringBootstrapApplicationServiceFactory preSpringBootstrapApplicationServiceFactory =
      new RecordingPreSpringBootstrapApplicationServiceFactory();

    int exitCode = Seed4JCliApp.productionBootstrapEntryPoint(userHome, true, preSpringBootstrapApplicationServiceFactory).launch(
      new String[] { "--version" }
    );

    assertThat(exitCode).isEqualTo(43);
    assertThat(preSpringBootstrapApplicationServiceFactory.userHome()).isEqualTo(userHome);
    assertThat(preSpringBootstrapApplicationServiceFactory.childMode()).isTrue();
    assertThat(preSpringBootstrapApplicationServiceFactory.arguments()).containsExactly("--version");
  }

  @Test
  void shouldResolveExecutableJarPathFromJavaCommandWhenCodeSourceIsNotAJar() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourcePath = tempDirectory.resolve("classes");
    Files.createDirectories(codeSourcePath);
    Path executableJarPath = tempDirectory.resolve("seed4j-cli.jar");
    Files.writeString(executableJarPath, "jar");

    Path executablePath = Seed4JCliApp.resolveExecutablePath(codeSourcePath, executableJarPath + " --version", "", tempDirectory);

    assertThat(executablePath).isEqualTo(executableJarPath);
  }

  @Test
  void shouldResolveExecutableJarPathFromClasspathWhenJavaCommandDoesNotStartWithJar() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourcePath = tempDirectory.resolve("classes");
    Files.createDirectories(codeSourcePath);
    Path executableJarPath = tempDirectory.resolve("seed4j-cli.jar");
    Files.writeString(executableJarPath, "jar");

    Path executablePath = Seed4JCliApp.resolveExecutablePath(
      codeSourcePath,
      "org.springframework.boot.loader.launch.PropertiesLauncher --version",
      executableJarPath.toString(),
      tempDirectory
    );

    assertThat(executablePath).isEqualTo(executableJarPath);
  }

  @Test
  void shouldResolveExecutableJarPathFromRelativeJavaCommandUsingCurrentWorkingDirectory() throws IOException {
    Path workingDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourcePath = workingDirectory.resolve("classes");
    Files.createDirectories(codeSourcePath);
    Path executableJarPath = workingDirectory.resolve("seed4j-cli.jar");
    Files.writeString(executableJarPath, "jar");

    Path executablePath = Seed4JCliApp.resolveExecutablePath(codeSourcePath, "seed4j-cli.jar --version", "", workingDirectory);

    assertThat(executablePath).isEqualTo(executableJarPath);
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

  private static final class RecordingPreSpringBootstrapApplicationServiceFactory implements PreSpringBootstrapApplicationServiceFactory {

    private Path userHome;
    private Boolean childMode;
    private String[] arguments;

    @Override
    public PreSpringBootstrapApplicationService create(Path userHomePath, boolean childMode) {
      this.userHome = userHomePath;
      this.childMode = childMode;
      return new PreSpringBootstrapApplicationService(
        (args, ignoredChildMode) -> {
          this.arguments = args;
          return 43;
        },
        childMode
      );
    }

    Path userHome() {
      return userHome;
    }

    Boolean childMode() {
      return childMode;
    }

    String[] arguments() {
      return arguments;
    }
  }
}
