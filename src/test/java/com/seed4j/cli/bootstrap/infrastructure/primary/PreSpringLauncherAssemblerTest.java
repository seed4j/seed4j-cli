package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.PreSpringLauncher;
import com.seed4j.cli.bootstrap.application.PreSpringLauncherFactory;
import com.seed4j.cli.bootstrap.application.PreSpringRuntimeEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringLauncherAssemblerTest {

  @Test
  void shouldBuildBootstrapCommandFromPrimaryInputAndReturnLauncherExitCode() {
    RecordingPreSpringLauncher recordingPreSpringLauncher = new RecordingPreSpringLauncher(53);
    RecordingPreSpringLauncherFactory recordingPreSpringLauncherFactory = new RecordingPreSpringLauncherFactory(recordingPreSpringLauncher);
    PreSpringLauncherAssembler assembler = new PreSpringLauncherAssembler(recordingPreSpringLauncherFactory);
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      Path.of("/home/user"),
      Path.of("/tmp/seed4j-cli.jar"),
      "2.2.0",
      true,
      Path.of("/tmp/jdk/bin/java")
    );

    int exitCode = assembler.exitCodeFor(runtimeEnvironment, new String[] { "--version" });

    assertThat(exitCode).isEqualTo(53);
    assertThat(recordingPreSpringLauncherFactory.userHomePath()).isEqualTo(Path.of("/home/user"));
    assertThat(recordingPreSpringLauncherFactory.executablePath()).isEqualTo(Path.of("/tmp/seed4j-cli.jar"));
    assertThat(recordingPreSpringLauncherFactory.currentSeed4JVersion()).isEqualTo("2.2.0");
    assertThat(recordingPreSpringLauncherFactory.javaExecutablePath()).isEqualTo(Path.of("/tmp/jdk/bin/java"));
    assertThat(recordingPreSpringLauncher.arguments()).containsExactly("--version");
    assertThat(recordingPreSpringLauncher.childMode()).isTrue();
  }

  @Test
  void shouldRunUsingInfrastructureCompositionInTheDefaultAssembler() throws IOException {
    Path userHomePath = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHomePath.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
    PreSpringLauncherAssembler assembler = new PreSpringLauncherAssembler();
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      userHomePath,
      Path.of("seed4j-cli.jar"),
      "2.2.0",
      true,
      Path.of(System.getProperty("java.home"), "bin", "java")
    );

    int exitCode = assembler.exitCodeFor(runtimeEnvironment, new String[] { "--version" });

    assertThat(exitCode).isZero();
  }

  private static final class RecordingPreSpringLauncher implements PreSpringLauncher {

    private final int exitCode;
    private String[] arguments;
    private Boolean childMode;

    private RecordingPreSpringLauncher(int exitCode) {
      this.exitCode = exitCode;
    }

    @Override
    public int launch(String[] args, boolean childMode) {
      this.arguments = args;
      this.childMode = childMode;
      return exitCode;
    }

    String[] arguments() {
      return arguments;
    }

    Boolean childMode() {
      return childMode;
    }
  }

  private static final class RecordingPreSpringLauncherFactory implements PreSpringLauncherFactory {

    private final RecordingPreSpringLauncher preSpringLauncher;
    private Path userHomePath;
    private Path executablePath;
    private String currentSeed4JVersion;
    private Path javaExecutablePath;

    private RecordingPreSpringLauncherFactory(RecordingPreSpringLauncher preSpringLauncher) {
      this.preSpringLauncher = preSpringLauncher;
    }

    @Override
    public PreSpringLauncher create(Path userHomePath, Path executablePath, String currentSeed4JVersion, Path javaExecutablePath) {
      this.userHomePath = userHomePath;
      this.executablePath = executablePath;
      this.currentSeed4JVersion = currentSeed4JVersion;
      this.javaExecutablePath = javaExecutablePath;
      return preSpringLauncher;
    }

    Path userHomePath() {
      return userHomePath;
    }

    Path executablePath() {
      return executablePath;
    }

    String currentSeed4JVersion() {
      return currentSeed4JVersion;
    }

    Path javaExecutablePath() {
      return javaExecutablePath;
    }
  }
}
