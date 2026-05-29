package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class CurrentProcessPreSpringRuntimeEnvironmentProviderTest {

  @Test
  void shouldResolveExecutableJarPathFromJavaCommandWhenCodeSourceIsNotAJar() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourcePath = tempDirectory.resolve("classes");
    Files.createDirectories(codeSourcePath);
    Path executableJarPath = tempDirectory.resolve("seed4j-cli.jar");
    Files.writeString(executableJarPath, "jar");

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentProvider.resolveExecutablePath(
      codeSourcePath,
      executableJarPath + " --version",
      "",
      tempDirectory
    );

    assertThat(executablePath).isEqualTo(executableJarPath);
  }

  @Test
  void shouldResolveExecutableJarPathFromClasspathWhenJavaCommandDoesNotStartWithJar() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourcePath = tempDirectory.resolve("classes");
    Files.createDirectories(codeSourcePath);
    Path executableJarPath = tempDirectory.resolve("seed4j-cli.jar");
    Files.writeString(executableJarPath, "jar");

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentProvider.resolveExecutablePath(
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

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentProvider.resolveExecutablePath(
      codeSourcePath,
      "seed4j-cli.jar --version",
      "",
      workingDirectory
    );

    assertThat(executablePath).isEqualTo(executableJarPath);
  }

  @Test
  void shouldResolveCurrentSeed4JVersionUsingFallbackWhenImplementationVersionIsNullOrBlank() {
    String versionWhenNull = CurrentProcessPreSpringRuntimeEnvironmentProvider.resolveCurrentSeed4JVersion(null);
    String versionWhenBlank = CurrentProcessPreSpringRuntimeEnvironmentProvider.resolveCurrentSeed4JVersion(" ");
    String versionWhenPresent = CurrentProcessPreSpringRuntimeEnvironmentProvider.resolveCurrentSeed4JVersion("2.2.0");

    assertThat(versionWhenNull).isEqualTo("0.0.0-SNAPSHOT");
    assertThat(versionWhenBlank).isEqualTo("0.0.0-SNAPSHOT");
    assertThat(versionWhenPresent).isEqualTo("2.2.0");
  }
}
