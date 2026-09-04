package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class CurrentProcessPreSpringRuntimeEnvironmentReaderTest {

  @Test
  void shouldResolveExecutableJarPathFromJavaCommandWhenCodeSourceIsNotAJar() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourcePath = tempDirectory.resolve("classes");
    Files.createDirectories(codeSourcePath);
    Path executableJarPath = tempDirectory.resolve("seed4j-cli.jar");
    Files.writeString(executableJarPath, "jar");

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveExecutablePath(
      codeSourcePath,
      new CurrentProcessPreSpringRuntimeEnvironmentReader.ProcessLaunchMetadata(executableJarPath + " --version", "", tempDirectory)
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

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveExecutablePath(
      codeSourcePath,
      new CurrentProcessPreSpringRuntimeEnvironmentReader.ProcessLaunchMetadata(
        "org.springframework.boot.loader.launch.PropertiesLauncher --version",
        executableJarPath.toString(),
        tempDirectory
      )
    );

    assertThat(executablePath).isEqualTo(executableJarPath);
  }

  @Test
  void shouldIgnoreMissingCommandJarInFavorOfValidClasspathJar() throws IOException {
    Path workingDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourcePath = Files.createDirectories(workingDirectory.resolve("classes"));
    Path missingCommandJarPath = workingDirectory.resolve("missing-seed4j-cli.jar");
    Path classpathJarPath = workingDirectory.resolve("classpath-seed4j-cli.jar");
    Files.writeString(classpathJarPath, "jar");

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveExecutablePath(
      codeSourcePath,
      new CurrentProcessPreSpringRuntimeEnvironmentReader.ProcessLaunchMetadata(
        missingCommandJarPath + " --version",
        classpathJarPath.toString(),
        workingDirectory
      )
    );

    assertThat(executablePath).isEqualTo(classpathJarPath);
  }

  @Test
  void shouldResolveExecutableJarPathFromRelativeJavaCommandUsingCurrentWorkingDirectory() throws IOException {
    Path workingDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourcePath = workingDirectory.resolve("classes");
    Files.createDirectories(codeSourcePath);
    Path executableJarPath = workingDirectory.resolve("seed4j-cli.jar");
    Files.writeString(executableJarPath, "jar");

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveExecutablePath(
      codeSourcePath,
      new CurrentProcessPreSpringRuntimeEnvironmentReader.ProcessLaunchMetadata("seed4j-cli.jar --version", "", workingDirectory)
    );

    assertThat(executablePath).isEqualTo(executableJarPath);
  }

  @Test
  void shouldKeepCodeSourcePathWhenCodeSourceIsARegularJar() throws IOException {
    Path workingDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourceJarPath = workingDirectory.resolve("seed4j-cli.jar");
    Path commandJarPath = workingDirectory.resolve("other-seed4j-cli.jar");
    Files.writeString(codeSourceJarPath, "jar");
    Files.writeString(commandJarPath, "other jar");

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveExecutablePath(
      codeSourceJarPath,
      new CurrentProcessPreSpringRuntimeEnvironmentReader.ProcessLaunchMetadata(commandJarPath + " --version", "", workingDirectory)
    );

    assertThat(executablePath).isEqualTo(codeSourceJarPath);
  }

  @Test
  void shouldFallbackToCodeSourcePathWhenNoExecutableCandidateIsValid() throws IOException {
    Path workingDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourcePath = workingDirectory.resolve("classes.bin");
    Path commandNonJarPath = workingDirectory.resolve("seed4j-cli");
    Path classpathJarPath = workingDirectory.resolve("missing-seed4j-cli.jar");
    Files.writeString(codeSourcePath, "classes");
    Files.writeString(commandNonJarPath, "not a jar");

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveExecutablePath(
      codeSourcePath,
      new CurrentProcessPreSpringRuntimeEnvironmentReader.ProcessLaunchMetadata(
        commandNonJarPath + " --version",
        classpathJarPath.toString(),
        workingDirectory
      )
    );

    assertThat(executablePath).isEqualTo(codeSourcePath);
  }
}
