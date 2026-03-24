package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

@UnitTest
class ExtensionRuntimeBootstrapListPackagedJarIT {

  private static final String EXTENSION_ONLY_SLUG = "runtime-extension-list-only";
  private static final Pattern MODULE_LINE_PATTERN = Pattern.compile("^\\s{2}(\\S+)\\s{2,}.+$");
  private static final Pattern MODULE_SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

  @Test
  void shouldNotListTheExtensionOnlySlugInStandardMode() throws IOException, InterruptedException {
    Path packagedCliJar = packagedCliJar();
    Path standardUserHome = Files.createTempDirectory("seed4j-cli-standard-");

    PackagedRunResult standardResult = runList(packagedCliJar, standardUserHome);

    List<String> standardSlugs = moduleSlugs(standardResult.output());
    assertThat(standardResult.finished()).isTrue();
    assertThat(standardResult.exitCode()).isZero();
    assertThat(standardSlugs).doesNotContain(EXTENSION_ONLY_SLUG).doesNotContain("Module").doesNotHaveDuplicates();
  }

  @Test
  void shouldListTheExtensionOnlySlugInExtensionMode() throws IOException, InterruptedException {
    Path packagedCliJar = packagedCliJar();
    Path extensionUserHome = Files.createTempDirectory("seed4j-cli-extension-");
    ExtensionRuntimeFixture.installWithListExtensionModule(extensionUserHome);

    PackagedRunResult extensionResult = runList(packagedCliJar, extensionUserHome);

    List<String> extensionSlugs = moduleSlugs(extensionResult.output());
    assertThat(extensionResult.finished()).isTrue();
    assertThat(extensionResult.exitCode()).isZero();
    assertThat(extensionSlugs).contains(EXTENSION_ONLY_SLUG).doesNotHaveDuplicates();
  }

  @Test
  void shouldKeepStandardCatalogAndAddOnlyTheExtensionOnlySlug() throws IOException, InterruptedException {
    Path packagedCliJar = packagedCliJar();
    Path standardUserHome = Files.createTempDirectory("seed4j-cli-standard-catalog-");
    Path extensionUserHome = Files.createTempDirectory("seed4j-cli-extension-catalog-");
    ExtensionRuntimeFixture.installWithListExtensionModule(extensionUserHome);

    PackagedRunResult standardResult = runList(packagedCliJar, standardUserHome);
    PackagedRunResult extensionResult = runList(packagedCliJar, extensionUserHome);

    List<String> standardSlugs = moduleSlugs(standardResult.output());
    List<String> extensionSlugs = moduleSlugs(extensionResult.output());
    Set<String> addedSlugs = setDifference(Set.copyOf(extensionSlugs), Set.copyOf(standardSlugs));
    Set<String> removedSlugs = setDifference(Set.copyOf(standardSlugs), Set.copyOf(extensionSlugs));
    assertThat(standardResult.finished()).isTrue();
    assertThat(standardResult.exitCode()).isZero();
    assertThat(extensionResult.finished()).isTrue();
    assertThat(extensionResult.exitCode()).isZero();
    assertThat(standardSlugs).doesNotHaveDuplicates();
    assertThat(extensionSlugs).doesNotHaveDuplicates();
    assertThat(standardSlugs).doesNotContain(EXTENSION_ONLY_SLUG);
    assertThat(extensionSlugs).contains(EXTENSION_ONLY_SLUG);
    assertThat(addedSlugs).containsExactly(EXTENSION_ONLY_SLUG);
    assertThat(removedSlugs).isEmpty();
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

  private static List<String> moduleSlugs(String output) {
    return output.lines().map(ExtensionRuntimeBootstrapListPackagedJarIT::moduleSlugFromLine).flatMap(Optional::stream).toList();
  }

  private static Optional<String> moduleSlugFromLine(String line) {
    Matcher moduleLineMatcher = MODULE_LINE_PATTERN.matcher(line);
    if (!moduleLineMatcher.matches()) {
      return Optional.empty();
    }

    String candidateSlug = moduleLineMatcher.group(1);
    if (!MODULE_SLUG_PATTERN.matcher(candidateSlug).matches()) {
      return Optional.empty();
    }

    return Optional.of(candidateSlug);
  }

  private static Set<String> setDifference(Set<String> sourceSlugs, Set<String> slugsToExclude) {
    Set<String> difference = new HashSet<>(sourceSlugs);
    difference.removeAll(slugsToExclude);
    return difference;
  }

  private record PackagedRunResult(boolean finished, int exitCode, String output) {}
}
