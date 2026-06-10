package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture;
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
class ExtensionRuntimeBootstrapListPackagedJarSmokeIT {

  private static final String EXTENSION_ONLY_SLUG = "runtime-extension-list-only";

  @Test
  void shouldListExtensionModuleThroughThePackagedJar() throws IOException, InterruptedException {
    Path packagedCliJar = packagedCliJar();
    Path extensionUserHome = Files.createTempDirectory("seed4j-cli-list-smoke-extension-");
    ExtensionRuntimeFixture.installWithListExtensionModule(extensionUserHome);

    PackagedRunResult listResult = runList(packagedCliJar, extensionUserHome);

    assertThat(listResult.finished()).isTrue();
    assertThat(listResult.exitCode()).isZero();
    assertThat(listResult.output()).contains(EXTENSION_ONLY_SLUG);
  }

  private static PackagedRunResult runList(Path packagedCliJar, Path userHome) throws IOException, InterruptedException {
    String[] command = { javaExecutablePath().toString(), "-Duser.home=" + userHome, "-jar", packagedCliJar.toString(), "list" };
    return runCommand(command);
  }

  private static PackagedRunResult runCommand(String[] command) throws IOException, InterruptedException {
    ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);

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
