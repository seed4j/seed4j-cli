package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliLauncherConstructionTest {

  @Test
  void shouldNotExposeLegacyRuntimeModeYamlHelpersInDomainPackage() {
    assertThatThrownBy(() -> Class.forName("com.seed4j.cli.bootstrap.domain.RuntimeModeConfigReader")).isExactlyInstanceOf(
      ClassNotFoundException.class
    );
    assertThatThrownBy(() -> Class.forName("com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationWriter")).isExactlyInstanceOf(
      ClassNotFoundException.class
    );
  }

  @Test
  void shouldNotExposeLegacyLauncherConstructorWithoutRuntimeModeConfigurationRepository() {
    assertThatThrownBy(() ->
      Seed4JCliLauncher.class.getDeclaredConstructor(Path.class, Path.class, ChildRuntimeLauncher.class, LocalCliRunner.class)
    ).isExactlyInstanceOf(NoSuchMethodException.class);
  }

  @Test
  void shouldCreateLauncherThatRunsStandardModeThroughTheProvidedChildProcessLauncher() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = Files.createTempFile("seed4j-cli-", ".jar");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      new Seed4JCliHome(userHome),
      executableJar,
      false,
      Path.of("/opt/jdk/bin/java")
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
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(runtimeEnvironment.executablePath(), runtimeEnvironment.childMode()),
      runtimeModeConfigurationRepository,
      RuntimeSelection::standard,
      childProcessLauncher,
      args -> {
        throw new IllegalStateException("Should not resolve local exit code in this test.");
      },
      executablePath -> true,
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isEqualTo(37);
    assertThat(childProcessLauncher.request().executableJar()).isEqualTo(executableJar);
    assertThat(childProcessLauncher.request().runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(childProcessLauncher.request().arguments().asList()).containsExactly("--version");
  }

  private static final class RecordingChildProcessLauncher implements ChildRuntimeLauncher {

    private ChildRuntimeLaunchRequest request;

    @Override
    public int launch(ChildRuntimeLaunchRequest request) {
      this.request = request;
      return 37;
    }

    ChildRuntimeLaunchRequest request() {
      return request;
    }
  }

  private static Seed4JCliArguments arguments(String... values) {
    return new Seed4JCliArguments(values);
  }

  private static Seed4JCliRuntime seed4jCliRuntime(Path executableJar, boolean childRuntime) {
    return new Seed4JCliRuntime() {
      @Override
      public Path executableJar() {
        return executableJar;
      }

      @Override
      public boolean childRuntime() {
        return childRuntime;
      }
    };
  }
}
