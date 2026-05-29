package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.Seed4JApp;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class CurrentProcessPreSpringRuntimeEnvironmentReaderTest {

  @Test
  void shouldReadCurrentRuntimeEnvironmentFromProcessProperties() throws IOException {
    String childModeProperty = "seed4j.cli.runtime.child";
    String originalUserHome = System.getProperty("user.home");
    String originalUserDir = System.getProperty("user.dir");
    String originalJavaClassPath = System.getProperty("java.class.path");
    String originalJavaCommand = System.getProperty("sun.java.command");
    String originalChildMode = System.getProperty(childModeProperty);
    Path workingDirectory = Files.createTempDirectory("seed4j-cli-");
    Path userHomePath = workingDirectory.resolve("home");
    Files.createDirectories(userHomePath);
    Path executableJarPath = workingDirectory.resolve("seed4j-cli.jar");
    Files.writeString(executableJarPath, "jar");
    Path expectedJavaExecutablePath = Path.of(System.getProperty("java.home"), "bin", "java");
    String expectedVersion = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveCurrentSeed4JVersion(
      Seed4JApp.class.getPackage().getImplementationVersion()
    );
    CurrentProcessPreSpringRuntimeEnvironmentReader reader = new CurrentProcessPreSpringRuntimeEnvironmentReader();

    try {
      System.setProperty("user.home", userHomePath.toString());
      System.setProperty("user.dir", workingDirectory.toString());
      System.setProperty("java.class.path", "");
      System.setProperty("sun.java.command", executableJarPath + " --version");
      System.setProperty(childModeProperty, "true");

      PreSpringRuntimeEnvironment environment = reader.current();

      assertThat(environment.userHomePath()).isEqualTo(userHomePath);
      assertThat(environment.executablePath()).isEqualTo(executableJarPath);
      assertThat(environment.currentSeed4JVersion()).isEqualTo(expectedVersion);
      assertThat(environment.childMode()).isTrue();
      assertThat(environment.javaExecutablePath()).isEqualTo(expectedJavaExecutablePath);
    } finally {
      restoreSystemProperty("user.home", originalUserHome);
      restoreSystemProperty("user.dir", originalUserDir);
      restoreSystemProperty("java.class.path", originalJavaClassPath);
      restoreSystemProperty("sun.java.command", originalJavaCommand);
      restoreSystemProperty(childModeProperty, originalChildMode);
    }
  }

  @Test
  void shouldResolveExecutableJarPathFromJavaCommandWhenCodeSourceIsNotAJar() throws IOException {
    Path tempDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourcePath = tempDirectory.resolve("classes");
    Files.createDirectories(codeSourcePath);
    Path executableJarPath = tempDirectory.resolve("seed4j-cli.jar");
    Files.writeString(executableJarPath, "jar");

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveExecutablePath(
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

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveExecutablePath(
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

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveExecutablePath(
      codeSourcePath,
      "seed4j-cli.jar --version",
      "",
      workingDirectory
    );

    assertThat(executablePath).isEqualTo(executableJarPath);
  }

  @Test
  void shouldKeepCodeSourcePathWhenCodeSourceIsARegularJar() throws IOException {
    Path workingDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourceJarPath = workingDirectory.resolve("seed4j-cli.jar");
    Files.writeString(codeSourceJarPath, "jar");

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveExecutablePath(
      codeSourceJarPath,
      "org.springframework.boot.loader.launch.PropertiesLauncher --version",
      "",
      workingDirectory
    );

    assertThat(executablePath).isEqualTo(codeSourceJarPath);
  }

  @Test
  void shouldFallbackToCodeSourcePathWhenJavaCommandAndClasspathAreBlank() throws IOException {
    Path workingDirectory = Files.createTempDirectory("seed4j-cli-");
    Path codeSourcePath = workingDirectory.resolve("classes.bin");
    Files.writeString(codeSourcePath, "classes");

    Path executablePath = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveExecutablePath(
      codeSourcePath,
      "   ",
      "   ",
      workingDirectory
    );

    assertThat(executablePath).isEqualTo(codeSourcePath);
  }

  @Test
  void shouldResolveCurrentSeed4JVersionUsingFallbackWhenImplementationVersionIsNullOrBlank() {
    String versionWhenNull = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveCurrentSeed4JVersion(null);
    String versionWhenBlank = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveCurrentSeed4JVersion(" ");
    String versionWhenPresent = CurrentProcessPreSpringRuntimeEnvironmentReader.resolveCurrentSeed4JVersion("2.2.0");

    assertThat(versionWhenNull).isEqualTo("0.0.0-SNAPSHOT");
    assertThat(versionWhenBlank).isEqualTo("0.0.0-SNAPSHOT");
    assertThat(versionWhenPresent).isEqualTo("2.2.0");
  }

  private static void restoreSystemProperty(String key, String originalValue) {
    if (originalValue == null) {
      System.clearProperty(key);
      return;
    }

    System.setProperty(key, originalValue);
  }
}
