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

  private static final String EXTENSION_APPLICATION_OVERRIDE_MARKER = "[EXT-APPLICATION-OVERRIDE]";
  private static final String EXTENSION_LOGBACK_OVERRIDE_MARKER = "[EXT-LOGBACK-OVERRIDE]";
  private static final String SPRING_BOOT_BANNER_MARKER = " :: Spring Boot :: ";
  private static final String STARTUP_INFO_MARKER = "Starting Seed4JCliApp";

  @Test
  void shouldKeepVersionOutputOperationallyCleanWhenExtensionPublishesRegressionOverrides() throws IOException, InterruptedException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-version-operational-clean-");
    ExtensionRuntimeFixture.installWithListExtensionModuleAndRegressionOverrides(userHome);
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
      .doesNotContain(EXTENSION_APPLICATION_OVERRIDE_MARKER)
      .doesNotContain(EXTENSION_LOGBACK_OVERRIDE_MARKER)
      .doesNotContain(SPRING_BOOT_BANNER_MARKER)
      .doesNotContain(STARTUP_INFO_MARKER)
      .doesNotContain("Missing watchable .xml or .properties files")
      .doesNotContain("Watching .xml files requires that the main configuration file is reachable as a URL")
      .contains("Runtime mode: extension")
      .contains("Distribution ID: company-extension")
      .contains("Distribution version: 1.0.0");
  }

  @Test
  void shouldKeepCliAndSeed4JVersionValuesStableComparedToStandardModeWhenExtensionPublishesRegressionOverrides()
    throws IOException, InterruptedException {
    Path packagedCliJar = packagedCliJar();
    Path standardUserHome = Files.createTempDirectory("seed4j-cli-standard-version-stability-");
    Path extensionUserHome = Files.createTempDirectory("seed4j-cli-extension-version-stability-");
    ExtensionRuntimeFixture.installWithListExtensionModuleAndRegressionOverrides(extensionUserHome);

    ProcessBuilder standardProcessBuilder = new ProcessBuilder(
      javaExecutablePath().toString(),
      "-Duser.home=" + standardUserHome,
      "-jar",
      packagedCliJar.toString(),
      "--version"
    ).redirectErrorStream(true);
    ProcessBuilder extensionProcessBuilder = new ProcessBuilder(
      javaExecutablePath().toString(),
      "-Duser.home=" + extensionUserHome,
      "-jar",
      packagedCliJar.toString(),
      "--version"
    ).redirectErrorStream(true);

    Process standardProcess = standardProcessBuilder.start();
    boolean standardFinished = standardProcess.waitFor(60, TimeUnit.SECONDS);
    String standardOutput = readOutput(standardProcess.getInputStream());
    Process extensionProcess = extensionProcessBuilder.start();
    boolean extensionFinished = extensionProcess.waitFor(60, TimeUnit.SECONDS);
    String extensionOutput = readOutput(extensionProcess.getInputStream());

    assertThat(standardFinished).isTrue();
    assertThat(standardProcess.exitValue()).isZero();
    assertThat(extensionFinished).isTrue();
    assertThat(extensionProcess.exitValue()).isZero();

    String standardCliVersionLine = lineWithPrefix(standardOutput, "Seed4J CLI v");
    String standardSeed4jVersionLine = lineWithPrefix(standardOutput, "Seed4J version: ");
    String extensionCliVersionLine = lineWithPrefix(extensionOutput, "Seed4J CLI v");
    String extensionSeed4jVersionLine = lineWithPrefix(extensionOutput, "Seed4J version: ");
    assertThat(extensionCliVersionLine).isEqualTo(standardCliVersionLine);
    assertThat(extensionSeed4jVersionLine).isEqualTo(standardSeed4jVersionLine);
  }

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

  private static String lineWithPrefix(String output, String prefix) {
    return output
      .lines()
      .filter(line -> line.startsWith(prefix))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Missing line: " + prefix));
  }
}
