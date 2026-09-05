package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class CurrentProcessPreSpringRuntimeEnvironmentReaderCurrentEnvironmentTest {

  @Test
  void shouldReadCurrentRuntimeEnvironmentFromProcessProperties() throws IOException {
    CurrentProcessPaths paths = CurrentProcessPaths.create();
    try (ProcessProperties processProperties = new ProcessProperties()) {
      processProperties.configureChildRuntime(paths);

      PreSpringRuntimeEnvironment environment = new CurrentProcessPreSpringRuntimeEnvironmentReader().current();

      assertThat(environment.cliHome()).isEqualTo(new Seed4JCliHome(paths.userHome()));
      assertThat(environment.executablePath().path()).isEqualTo(paths.executableJar());
      assertThat(environment.processMode().child()).isTrue();
      assertThat(environment.javaExecutablePath().path()).isEqualTo(Path.of(System.getProperty("java.home"), "bin", "java"));
    }
  }

  @Test
  void shouldFallBackToCodeSourceInParentModeWithoutLaunchMetadata() throws IOException, URISyntaxException {
    CurrentProcessPaths paths = CurrentProcessPaths.create();
    Path expectedExecutablePath = Path.of(Seed4JCliApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    try (ProcessProperties processProperties = new ProcessProperties()) {
      processProperties.configureParentRuntimeWithoutLaunchMetadata(paths);

      PreSpringRuntimeEnvironment environment = new CurrentProcessPreSpringRuntimeEnvironmentReader().current();

      assertThat(environment.cliHome()).isEqualTo(new Seed4JCliHome(paths.userHome()));
      assertThat(environment.executablePath().path()).isEqualTo(expectedExecutablePath);
      assertThat(environment.processMode().child()).isFalse();
      assertThat(environment.javaExecutablePath().path()).isEqualTo(Path.of(System.getProperty("java.home"), "bin", "java"));
    }
  }

  private record CurrentProcessPaths(Path workingDirectory, Path userHome, Path executableJar) {
    private static CurrentProcessPaths create() throws IOException {
      Path workingDirectory = Files.createTempDirectory("seed4j-cli-");
      Path userHome = Files.createDirectories(workingDirectory.resolve("home"));
      Path executableJar = Files.writeString(workingDirectory.resolve("seed4j-cli.jar"), "jar");
      return new CurrentProcessPaths(workingDirectory, userHome, executableJar);
    }
  }

  private static final class ProcessProperties implements AutoCloseable {

    private static final String CHILD_MODE_PROPERTY = "seed4j.cli.runtime.child";
    private static final List<String> MANAGED_PROPERTIES = List.of(
      "user.home",
      "user.dir",
      "java.class.path",
      "sun.java.command",
      CHILD_MODE_PROPERTY
    );

    private final List<OriginalProperty> originals = MANAGED_PROPERTIES.stream().map(OriginalProperty::capture).toList();

    private void configureChildRuntime(CurrentProcessPaths paths) {
      System.setProperty("user.home", paths.userHome().toString());
      System.setProperty("user.dir", paths.workingDirectory().toString());
      System.setProperty("java.class.path", "");
      System.setProperty("sun.java.command", paths.executableJar() + " --version");
      System.setProperty(CHILD_MODE_PROPERTY, "true");
    }

    private void configureParentRuntimeWithoutLaunchMetadata(CurrentProcessPaths paths) {
      System.setProperty("user.home", paths.userHome().toString());
      System.setProperty("user.dir", paths.workingDirectory().toString());
      System.clearProperty("java.class.path");
      System.clearProperty("sun.java.command");
      System.clearProperty(CHILD_MODE_PROPERTY);
    }

    @Override
    public void close() {
      originals.forEach(OriginalProperty::restore);
    }
  }

  private record OriginalProperty(String key, String value) {
    private static OriginalProperty capture(String key) {
      return new OriginalProperty(key, System.getProperty(key));
    }

    private void restore() {
      if (value == null) {
        System.clearProperty(key);
        return;
      }
      System.setProperty(key, value);
    }
  }
}
