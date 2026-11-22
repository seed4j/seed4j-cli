package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@UnitTest
class Seed4JPackagedJarIT {

  @TempDir
  private Path workspace;

  private Path home;
  private Path jar;

  @BeforeEach
  void prepareProcess() throws IOException {
    home = Files.createDirectory(workspace.resolve("effective home"));
    try (Stream<Path> files = Files.list(Path.of("target"))) {
      jar = files
        .filter(path -> path.getFileName().toString().matches("seed4j-cli-.*\\.jar"))
        .findFirst()
        .orElseThrow()
        .toAbsolutePath();
    }
  }

  @Test
  void shouldUseTheEffectiveJavaHomeForExternalCliConfiguration() throws IOException, InterruptedException {
    Path config = home.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(config.getParent());
    Files.writeString(
      config,
      """
      seed4j:
        hidden-resources:
          slugs: [gradle-java]
      """
    );

    Run result = cli("list");

    assertThat(result.exitCode()).withFailMessage(result.err()).isZero();
    assertThat(result.out()).containsPattern("(?m)^  init\\s").doesNotContainPattern("(?m)^  gradle-java\\s");
  }

  @Test
  void shouldReportThePackagedEngineVersionAndShipOnlyTheRuntimeModules() throws IOException, InterruptedException {
    Properties distribution = distribution();
    List<String> seed4jLibraries;
    try (JarFile packaged = new JarFile(jar.toFile())) {
      seed4jLibraries = packaged
        .stream()
        .map(entry -> entry.getName())
        .filter(name -> name.startsWith("BOOT-INF/lib/seed4j"))
        .toList();
    }
    String[] coordinate = distribution.getProperty("seed4j-dependency-coordinate").split(":");
    String version = coordinate[2];

    Run result = cli("--version");

    assertThat(result.exitCode()).withFailMessage(result.err()).isZero();
    assertThat(result.out()).contains("Seed4J version: " + version, "Runtime mode: standard");
    assertThat(seed4jLibraries).containsExactlyInAnyOrder(
      "BOOT-INF/lib/seed4j-api-" + version + ".jar",
      "BOOT-INF/lib/seed4j-engine-" + version + ".jar",
      "BOOT-INF/lib/seed4j-core-modules-" + version + ".jar"
    );
    assertThat(coordinate[1]).isEqualTo("seed4j-engine");
    if ("experimental".equals(distribution.getProperty("release-channel"))) {
      assertThat(result.out()).contains(
        "Release channel: experimental",
        "Seed4J upstream commit: " + version.substring(version.lastIndexOf('.') + 1).replace("-SNAPSHOT", "")
      );
    }
  }

  @Test
  void shouldExposeTheGeneratorOnlyOnTheStableChannel() throws IOException, InterruptedException {
    boolean experimental = "experimental".equals(distribution().getProperty("release-channel"));
    Path project = workspace.resolve("generator-project");

    Run catalog = cli("list");

    assertThat(catalog.exitCode()).withFailMessage(catalog.err()).isZero();
    assertThat(catalog.out()).containsPattern("(?m)^  init\\s").containsPattern("(?m)^  maven-java\\s");

    if (experimental) {
      Run apply = cli("apply", "seed4j-extension", "--project-path", project.toString());
      Run applySet = cli("apply-set", "seed4j-extension", "--project-path", project.toString());

      assertThat(catalog.out()).doesNotContainPattern("(?m)^  seed4j-extension\\s");
      assertThat(apply.exitCode()).isEqualTo(2);
      assertThat(apply.err()).contains("seed4j-extension", "unavailable in the experimental channel");
      assertThat(applySet.exitCode()).isEqualTo(2);
      assertThat(applySet.err()).contains("seed4j-extension", "unavailable in the experimental channel");
      assertThat(project).doesNotExist();
    } else {
      Run help = cli("apply", "seed4j-extension", "--help");

      assertThat(catalog.out()).containsPattern("(?m)^  seed4j-extension\\s");
      assertThat(help.exitCode()).withFailMessage(help.err()).isZero();
      assertThat(help.out()).contains("seed4j apply seed4j-extension");
    }
  }

  @ParameterizedTest
  @CsvSource({ "legacy-aggregated.json, history.json", "legacy-action.json, 20261020120000-init.json" })
  void shouldReadLegacyHistoryAndPreferExplicitOptionsWithoutChangingProject(String fixture, String filename)
    throws IOException, InterruptedException
  {
    Path project = workspace.resolve("legacy-project");
    Path history = Files.createDirectories(project.resolve(".seed4j/modules"));
    try (InputStream input = getClass().getResourceAsStream("/history/" + fixture)) {
      Files.copy(input, history.resolve(filename));
    }
    Files.writeString(project.resolve("existing.txt"), "keep this file");
    Map<Path, String> filesBefore = snapshot(project);

    Run plan = cli(
      "apply",
      "maven-java",
      "--project-path",
      project.toString(),
      "--project-name",
      "Explicit project name",
      "--package-name",
      "com.example.project",
      "--plan"
    );
    Run setPlan = cli(
      "apply-set",
      "maven-java",
      "--project-path",
      project.toString(),
      "--project-name",
      "Explicit project name",
      "--package-name",
      "com.example.project",
      "--plan"
    );

    assertThat(plan.exitCode()).withFailMessage(plan.err()).isZero();
    assertThat(plan.out()).contains(
      "module:init - already applied",
      "projectName: Explicit project name\n  Source: explicit CLI input",
      "baseName: legacyIntegrationApp\n  Source: project history"
    );
    assertThat(setPlan.exitCode()).withFailMessage(setPlan.err()).isZero();
    assertThat(setPlan.out()).contains("Explicit project name", "legacyIntegrationApp", "Status: VALID");
    assertThat(snapshot(project)).isEqualTo(filesBefore);
  }

  @Test
  void shouldWriteOneHistoryActionForAnAppliedModule() throws IOException, InterruptedException {
    Path project = Files.createDirectory(workspace.resolve("new-project"));

    Run apply = cli(
      "apply",
      "init",
      "--project-path",
      project.toString(),
      "--project-name",
      "Sample project",
      "--base-name",
      "sampleProject",
      "--node-package-manager",
      "npm",
      "--no-commit"
    );

    assertThat(apply.exitCode()).withFailMessage(apply.err()).isZero();
    assertThat(project.resolve("README.md")).exists();
    assertThat(project.resolve(".git")).doesNotExist();
    try (Stream<Path> history = Files.list(project.resolve(".seed4j/modules"))) {
      assertThat(history.toList())
        .singleElement()
        .satisfies(action -> {
          assertThat(action.getFileName().toString()).endsWith("-init.json");
          assertThat(Files.readString(action)).contains("\"module\" : \"init\"", "Sample project", "sampleProject");
        });
    }
  }

  @Test
  void shouldPreserveAnOldInstallationDuringTheTransition() throws IOException, InterruptedException {
    Path config = home.resolve(".config/seed4j-cli/config.yml");
    Path runtime = config.getParent().resolve("runtime");
    Path installedJar = Files.createDirectories(runtime.resolve("active")).resolve("extension.jar");
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(installedJar))) {
      jar.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jar.closeEntry();
    }
    Files.writeString(installedJar.resolveSibling("metadata.yml"), "distribution:\n  id: company\n  version: '1'\n");
    Files.writeString(Files.createDirectories(runtime.resolve("cache")).resolve("sentinel"), "original cached content");
    String legacy = "seed4j:\n  runtime:\n    mode: extension\n";
    Files.writeString(config, legacy);
    Map<Path, String> beforeUpgrade = snapshot(home);

    Run rejected = cli("--version");

    assertThat(rejected.exitCode()).isEqualTo(1);
    assertThat(rejected.out()).isEmpty();
    assertThat(rejected.err()).contains("seed4j.runtime.mode: standard", "~/.config/seed4j-cli/config.yml");
    assertThat(snapshot(home)).isEqualTo(beforeUpgrade);

    Files.writeString(config, legacy.replace("extension", "standard"));
    Map<Path, String> afterConfigurationEdit = snapshot(home);

    Run recovered = cli("list");
    Run help = cli("extension", "--help");
    Run install = cli("extension", "install", installedJar.toString(), "--distribution-id", "company", "--distribution-version", "1");
    Run enable = cli("extension", "enable");
    Run disable = cli("extension", "disable");

    assertThat(recovered.exitCode()).withFailMessage(recovered.err()).isZero();
    assertThat(recovered.out()).containsPattern("(?m)^  init\\s");
    assertThat(help.exitCode()).isZero();
    assertThat(help.out()).contains("Loading runtime extension JARs is no longer supported.");
    for (Run action : List.of(install, enable, disable)) {
      assertThat(action.exitCode()).isEqualTo(2);
      assertThat(action.err()).contains("Loading runtime extension JARs is no longer supported.");
    }
    assertThat(snapshot(home)).isEqualTo(afterConfigurationEdit);
  }

  private Properties distribution() throws IOException {
    try (
      JarFile packaged = new JarFile(jar.toFile());
      InputStream metadata = packaged.getInputStream(packaged.getJarEntry("META-INF/seed4j-cli-distribution.properties"))
    ) {
      Properties distribution = new Properties();
      distribution.load(metadata);
      return distribution;
    }
  }

  private static Map<Path, String> snapshot(Path root) throws IOException {
    Map<Path, String> contents = new HashMap<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.toList()) {
        contents.put(
          root.relativize(file),
          Files.isDirectory(file) ? "directory" : Base64.getEncoder().encodeToString(Files.readAllBytes(file))
        );
      }
    }
    return contents;
  }

  private Run cli(String... arguments) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>(
      List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.home=" + home, "-jar", jar.toString())
    );
    command.addAll(List.of(arguments));
    Path stdout = workspace.resolve("cli.stdout");
    Path stderr = workspace.resolve("cli.stderr");
    Process process = new ProcessBuilder(command)
      .directory(workspace.toFile())
      .redirectOutput(stdout.toFile())
      .redirectError(stderr.toFile())
      .start();
    if (!process.waitFor(45, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IllegalStateException("Packaged CLI timed out: " + Files.readString(stderr));
    }
    return new Run(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
  }

  private record Run(int exitCode, String out, String err) {}
}
