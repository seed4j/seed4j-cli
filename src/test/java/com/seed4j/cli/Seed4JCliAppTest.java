package com.seed4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliAppTest {

  @Test
  void shouldForwardArgumentsWhenRunningProductionPath() {
    RecordingBootstrapExitCodeResolver bootstrapExitCodeResolver = new RecordingBootstrapExitCodeResolver();

    Seed4JCliApp.productionExitCode(new String[] { "--version" }, bootstrapExitCodeResolver);

    assertThat(bootstrapExitCodeResolver.arguments()).containsExactly("--version");
  }

  @Test
  void shouldExitWithCodeReturnedByProductionBootstrapWhenRunningProductionPath() {
    RecordingBootstrapExitCodeResolver bootstrapExitCodeResolver = new RecordingBootstrapExitCodeResolver(79);

    int exitCode = Seed4JCliApp.productionExitCode(new String[] { "--version" }, bootstrapExitCodeResolver);

    assertThat(exitCode).isEqualTo(79);
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

    int exitCode = Seed4JCliApp.productionExitCode(
      new String[] { "--version" },
      Seed4JCliApp.productionBootstrapExitCodeResolver(userHome, true)
    );

    assertThat(exitCode).isZero();
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

  private static final class RecordingBootstrapExitCodeResolver implements Seed4JCliApp.BootstrapExitCodeResolver {

    private final int exitCode;
    private String[] arguments;

    private RecordingBootstrapExitCodeResolver() {
      this(23);
    }

    private RecordingBootstrapExitCodeResolver(int exitCode) {
      this.exitCode = exitCode;
    }

    @Override
    public int exitCodeFor(String[] args) {
      this.arguments = args;
      return exitCode;
    }

    String[] arguments() {
      return arguments;
    }
  }
}
