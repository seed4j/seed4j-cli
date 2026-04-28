package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

@UnitTest
class ExtensionRuntimeBootstrapPackagedJarIT {

  @Test
  void shouldRunThePackagedJarInExtensionMode() throws IOException, InterruptedException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    ExtensionRuntimeFixture.install(userHome);
    Path packagedCliJar = packagedCliJar();
    ProcessBuilder processBuilder = new ProcessBuilder(
      javaExecutablePath().toString(),
      "-Duser.home=" + userHome,
      "-jar",
      packagedCliJar.toString(),
      "--version"
    ).redirectErrorStream(true);

    Process process = processBuilder.start();
    boolean finished = process.waitFor(60, TimeUnit.SECONDS);
    String output = readOutput(process.getInputStream());

    assertThat(finished).isTrue();
    assertThat(process.exitValue()).isZero();
    assertThat(output)
      .contains("Runtime mode: extension")
      .contains("Distribution ID: company-extension")
      .contains("Distribution version: 1.0.0");
  }

  @Test
  void shouldFailBeforeRunningThePackagedJarWhenExtensionRuntimeJarIsFlat() throws IOException, InterruptedException {
    Path userHome = Files.createTempDirectory("seed4j-cli-invalid-extension-");
    ExtensionRuntimeFixture.installWithFlatJar(userHome);
    Path packagedCliJar = packagedCliJar();
    ProcessBuilder processBuilder = new ProcessBuilder(
      javaExecutablePath().toString(),
      "-Duser.home=" + userHome,
      "-jar",
      packagedCliJar.toString(),
      "--version"
    ).redirectErrorStream(true);

    Process process = processBuilder.start();
    boolean finished = process.waitFor(60, TimeUnit.SECONDS);
    String output = readOutput(process.getInputStream());

    assertThat(finished).isTrue();
    assertThat(process.exitValue()).isNotZero();
    assertThat(output).contains("BOOT-INF/classes");
  }

  private static Path packagedCliJar() throws IOException {
    Path targetDirectory = Path.of("target");
    List<Path> candidateJars;

    try (Stream<Path> targetFiles = Files.list(targetDirectory)) {
      candidateJars = targetFiles
        .filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().startsWith("seed4j-cli-"))
        .filter(path -> path.getFileName().toString().endsWith(".jar"))
        .filter(path -> !path.getFileName().toString().endsWith(".jar.original"))
        .sorted()
        .toList();
    }

    if (candidateJars.size() != 1) {
      throw new IllegalStateException("Expected exactly one packaged seed4j-cli jar in target/, found: " + candidateJars);
    }

    return candidateJars.getFirst();
  }

  private static Path javaExecutablePath() {
    return Path.of(System.getProperty("java.home"), "bin", "java");
  }

  private static String readOutput(InputStream processOutput) throws IOException {
    try (InputStream inputStream = processOutput) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
