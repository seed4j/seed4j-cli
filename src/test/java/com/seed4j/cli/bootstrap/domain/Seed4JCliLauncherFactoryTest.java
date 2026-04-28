package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
  void shouldKeepLauncherConstructionInternalToTheBootstrapPackage() throws NoSuchMethodException {
    Constructor<Seed4JCliLauncher> constructor = Seed4JCliLauncher.class.getDeclaredConstructor(
      Path.class,
      Path.class,
      String.class,
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
    Seed4JCliLauncherFactory factory = new Seed4JCliLauncherFactory();

    Seed4JCliLauncher launcher = factory.create(
      userHome,
      executableJar,
      "0.0.1-SNAPSHOT",
      "2.2.0",
      Path.of("/opt/jdk/bin/java"),
      commandExecutor,
      () -> {
        throw new IllegalStateException("Should not run local path in this test.");
      },
      context -> {
        throw new IllegalStateException("Should not resolve local exit code in this test.");
      }
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isEqualTo(37);
    assertThat(commandExecutor.command()).containsExactly(
      "/opt/jdk/bin/java",
      "-Dseed4j.cli.runtime.child=true",
      "-Dseed4j.cli.runtime.mode=standard",
      "-Dseed4j.cli.seed4j.version=2.2.0",
      "-Dseed4j.cli.version=0.0.1-SNAPSHOT",
      "-cp",
      executableJar.toString(),
      "org.springframework.boot.loader.launch.PropertiesLauncher",
      "--version"
    );
  }

  private static final class RecordingCommandExecutor implements Seed4JCliLauncherFactory.CommandExecutor {

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
