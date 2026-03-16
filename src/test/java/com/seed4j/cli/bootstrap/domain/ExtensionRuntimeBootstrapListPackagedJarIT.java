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
class ExtensionRuntimeBootstrapListPackagedJarIT {

  private static final String EXTENSION_ONLY_SLUG = "runtime-extension-list-only";

  @Test
  void shouldListTheExtensionOnlySlugOnlyInExtensionMode() throws IOException, InterruptedException {
    Path packagedCliJar = packagedCliJar();
    Path standardUserHome = Files.createTempDirectory("seed4j-cli-standard-");
    Path extensionUserHome = Files.createTempDirectory("seed4j-cli-extension-");
    ExtensionRuntimeFixture.installWithListExtensionModule(extensionUserHome);

    PackagedRunResult standardResult = runList(packagedCliJar, standardUserHome);
    PackagedRunResult extensionResult = runList(packagedCliJar, extensionUserHome);

    assertThat(standardResult.finished()).isTrue();
    assertThat(standardResult.exitCode()).isZero();
    assertThat(standardResult.output()).doesNotContain(EXTENSION_ONLY_SLUG);

    assertThat(extensionResult.finished()).isTrue();
    assertThat(extensionResult.exitCode()).isZero();
    assertThat(extensionResult.output()).contains(EXTENSION_ONLY_SLUG);
  }

  private static PackagedRunResult runList(Path packagedCliJar, Path userHome) throws IOException, InterruptedException {
    ProcessBuilder processBuilder = new ProcessBuilder(
      javaExecutablePath().toString(),
      "-Duser.home=" + userHome,
      "-jar",
      packagedCliJar.toString(),
      "list"
    ).redirectErrorStream(true);

    Process process = processBuilder.start();
    boolean finished = process.waitFor(60, TimeUnit.SECONDS);
    String output = readOutput(process.getInputStream());
    if (!finished) {
      process.destroyForcibly();
      return new PackagedRunResult(false, -1, output);
    }

    return new PackagedRunResult(true, process.exitValue(), output);
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

  private record PackagedRunResult(boolean finished, int exitCode, String output) {}
}
