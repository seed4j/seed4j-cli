package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
      Seed4JCliLauncher.class.getDeclaredConstructor(Path.class, Path.class, ChildProcessLauncher.class, LocalCliRunner.class)
    ).isExactlyInstanceOf(NoSuchMethodException.class);
  }

  @Test
  void shouldKeepLauncherConstructionInternalToTheBootstrapPackage() throws NoSuchMethodException {
    Constructor<Seed4JCliLauncher> constructor = Seed4JCliLauncher.class.getDeclaredConstructor(
      Path.class,
      Path.class,
      RuntimeModeConfigurationRepository.class,
      ChildProcessLauncher.class,
      LocalCliRunner.class
    );

    assertThat(Modifier.isPublic(constructor.getModifiers())).isFalse();
  }

  @Test
  void shouldCreateLauncherThatRunsStandardModeThroughTheProvidedCommandExecutor() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = Files.createTempFile("seed4j-cli-", ".jar");
    RecordingCommandExecutor commandExecutor = new RecordingCommandExecutor();
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
      Path.of("/opt/jdk/bin/java"),
      commandExecutor,
      args -> {
        throw new IllegalStateException("Should not resolve local exit code in this test.");
      }
    );

    Seed4JCliLauncher launcher = factory.create(userHome, executableJar, runtimeModeConfigurationRepository, dependencies);

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isEqualTo(37);
    assertThat(commandExecutor.command()).containsExactly(
      "/opt/jdk/bin/java",
      "-Dseed4j.cli.runtime.child=true",
      "-Dseed4j.cli.runtime.mode=standard",
      "-cp",
      executableJar.toString(),
      "org.springframework.boot.loader.launch.PropertiesLauncher",
      "--version"
    );
  }

  private static final class RecordingCommandExecutor implements ProcessCommandExecutor {

    private List<String> command;

    @Override
    public int execute(List<String> command) {
      this.command = command;
      return 37;
    }

    List<String> command() {
      return command;
    }
  }
}
