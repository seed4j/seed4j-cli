package com.seed4j.cli.bootstrap.infrastructure.primary;

import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.javaExecutablePath;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.launchCapturingOutput;

import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.domain.BootstrapDiagnostics;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLauncher;
import com.seed4j.cli.bootstrap.domain.JavaExecutablePath;
import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.RuntimeProcessMode;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliExecutablePath;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.CliLaunchResult;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionSelectionRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JarRuntimeExtensionPackageValidator;
import com.seed4j.cli.bootstrap.infrastructure.secondary.PreSpringRuntimeEnvironmentSeed4JCliRuntime;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class PrimaryBootstrapFixture {

  private final Path executablePath;
  private final RecordingChildRuntimeLauncher childRuntimeLauncher;
  private final RecordingLocalCliRunner localCliRunner;
  private final RecordingBootstrapDiagnostics bootstrapDiagnostics;
  private final PreSpringBootstrapRunner runner;

  private PrimaryBootstrapFixture(Path userHome, Path executablePath, RuntimeProcessMode processMode) {
    this.executablePath = executablePath;
    Seed4JCliHome cliHome = new Seed4JCliHome(userHome);
    childRuntimeLauncher = new RecordingChildRuntimeLauncher();
    localCliRunner = new RecordingLocalCliRunner();
    bootstrapDiagnostics = new RecordingBootstrapDiagnostics();
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      cliHome,
      new Seed4JCliExecutablePath(executablePath),
      processMode,
      new JavaExecutablePath(javaExecutablePath())
    );
    runner = runner(cliHome, runtimeEnvironment);
  }

  private PreSpringBootstrapRunner runner(Seed4JCliHome cliHome, PreSpringRuntimeEnvironment runtimeEnvironment) {
    PreSpringBootstrapApplicationService applicationService = new PreSpringBootstrapApplicationService(
      new PreSpringRuntimeEnvironmentSeed4JCliRuntime(runtimeEnvironment),
      new FileSystemRuntimeModeConfigurationRepository(cliHome),
      new FileSystemRuntimeExtensionSelectionRepository(cliHome, new JarRuntimeExtensionPackageValidator()),
      childRuntimeLauncher,
      localCliRunner,
      new FileSystemPackagedExecutableDetector(),
      bootstrapDiagnostics,
      new SystemErrBootstrapOutput()
    );
    return new PreSpringBootstrapRunner(applicationService);
  }

  static PrimaryBootstrapFixture packaged(Path userHome) throws IOException {
    return new PrimaryBootstrapFixture(userHome, Files.createTempFile("seed4j-cli-", ".jar"), RuntimeProcessMode.PARENT);
  }

  static PrimaryBootstrapFixture unpackaged(Path userHome, Path executableDirectory) {
    return new PrimaryBootstrapFixture(userHome, executableDirectory, RuntimeProcessMode.PARENT);
  }

  static PrimaryBootstrapFixture child(Path userHome, Path executableJar) {
    return new PrimaryBootstrapFixture(userHome, executableJar, RuntimeProcessMode.CHILD);
  }

  BootstrapLaunch launch(String... arguments) {
    CliLaunchResult result = launchCapturingOutput(runner, arguments);
    return new BootstrapLaunch(result, childRuntimeLauncher.request());
  }

  Path executablePath() {
    return executablePath;
  }

  ChildRuntimeLaunchRequest childLaunchRequest() {
    return childRuntimeLauncher.request();
  }

  void childRuntimeReturns(int exitCode) {
    childRuntimeLauncher.returns(exitCode);
  }

  List<String> localRunArguments() {
    return localCliRunner.arguments();
  }

  boolean debugLoggingEnabled() {
    return bootstrapDiagnostics.enabled();
  }

  record BootstrapLaunch(CliLaunchResult result, ChildRuntimeLaunchRequest childRuntimeRequest) {}

  private static final class RecordingChildRuntimeLauncher implements ChildRuntimeLauncher {

    private ChildRuntimeLaunchRequest request;
    private int exitCode;

    @Override
    public int launch(ChildRuntimeLaunchRequest request) {
      this.request = request;
      return exitCode;
    }

    private ChildRuntimeLaunchRequest request() {
      return request;
    }

    private void returns(int exitCode) {
      this.exitCode = exitCode;
    }
  }

  private static final class RecordingLocalCliRunner implements LocalCliRunner {

    private List<String> arguments = List.of();

    @Override
    public int run(Seed4JCliArguments arguments) {
      this.arguments = arguments.asList();
      return 12;
    }

    private List<String> arguments() {
      return arguments;
    }
  }

  private static final class RecordingBootstrapDiagnostics implements BootstrapDiagnostics {

    private boolean enabled;

    @Override
    public void enableDebugLogging() {
      enabled = true;
    }

    private boolean enabled() {
      return enabled;
    }
  }
}
