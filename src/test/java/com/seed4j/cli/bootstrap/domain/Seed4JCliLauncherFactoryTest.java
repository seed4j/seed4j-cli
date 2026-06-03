package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliLauncherFactoryTest {

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
  void shouldKeepLauncherConstructionInternalToTheBootstrapPackage() throws NoSuchMethodException {
    Constructor<Seed4JCliLauncher> constructor = Seed4JCliLauncher.class.getDeclaredConstructor(
      Path.class,
      RuntimeModeConfigurationRepository.class,
      RuntimeExtensionSelectionRepository.class,
      ChildRuntimeLauncher.class,
      LocalCliRunner.class,
      PackagedExecutableDetector.class,
      BootstrapDiagnostics.class,
      BootstrapOutput.class,
      boolean.class
    );

    assertThat(Modifier.isPublic(constructor.getModifiers())).isFalse();
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
    Seed4JCliLauncherFactory factory = new Seed4JCliLauncherFactory();
    Seed4JCliLauncherFactory.LauncherDependencies dependencies = new Seed4JCliLauncherFactory.LauncherDependencies(
      childProcessLauncher,
      args -> {
        throw new IllegalStateException("Should not resolve local exit code in this test.");
      },
      executablePath -> true,
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    Seed4JCliLauncher launcher = factory.create(
      runtimeEnvironment,
      runtimeModeConfigurationRepository,
      RuntimeSelection::standard,
      dependencies
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
}
