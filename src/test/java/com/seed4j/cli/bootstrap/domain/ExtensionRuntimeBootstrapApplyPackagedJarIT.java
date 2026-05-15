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
class ExtensionRuntimeBootstrapApplyPackagedJarIT {

  private static final String OVERRIDDEN_PRETTIER_VERSION = "3.6.2";
  private static final String OVERRIDDEN_PRETTIER_TEMPLATE_MARKER = "seed4j-extension-template-override";

  @Test
  void shouldKeepCorePrettierBaselineInExtensionModeWhenExtensionHasNoSourceOrTemplateCollision() throws IOException, InterruptedException {
    Path packagedCliJar = packagedCliJar();
    Path standardUserHome = Files.createTempDirectory("seed4j-cli-apply-standard-");
    Path extensionUserHome = Files.createTempDirectory("seed4j-cli-apply-extension-");
    ExtensionRuntimeFixture.installWithApplyControlExtensionModule(extensionUserHome);
    Path standardProjectPath = Files.createTempDirectory("seed4j-cli-apply-standard-project-");
    Path extensionProjectPath = Files.createTempDirectory("seed4j-cli-apply-extension-project-");

    PackagedRunResult standardInitResult = runApplyInit(packagedCliJar, standardUserHome, standardProjectPath);
    PackagedRunResult extensionInitResult = runApplyInit(packagedCliJar, extensionUserHome, extensionProjectPath);
    PackagedRunResult standardPrettierResult = runApplyPrettier(packagedCliJar, standardUserHome, standardProjectPath);
    PackagedRunResult extensionPrettierResult = runApplyPrettier(packagedCliJar, extensionUserHome, extensionProjectPath);

    String standardPackageJson = Files.readString(standardProjectPath.resolve("package.json"));
    String extensionPackageJson = Files.readString(extensionProjectPath.resolve("package.json"));
    String standardPrettierConfiguration = Files.readString(standardProjectPath.resolve(".prettierrc"));
    String extensionPrettierConfiguration = Files.readString(extensionProjectPath.resolve(".prettierrc"));

    assertThat(standardInitResult.finished()).isTrue();
    assertThat(standardInitResult.exitCode()).isZero();
    assertThat(extensionInitResult.finished()).isTrue();
    assertThat(extensionInitResult.exitCode()).isZero();
    assertThat(standardPrettierResult.finished()).isTrue();
    assertThat(standardPrettierResult.exitCode()).isZero();
    assertThat(extensionPrettierResult.finished()).isTrue();
    assertThat(extensionPrettierResult.exitCode()).isZero();
    assertThat(extensionPackageJson).isEqualTo(standardPackageJson);
    assertThat(extensionPrettierConfiguration).isEqualTo(standardPrettierConfiguration);
  }

  @Test
  void shouldOverrideCorePrettierDependencyVersionsWhenExtensionCollidesOnCommonSource() throws IOException, InterruptedException {
    Path packagedCliJar = packagedCliJar();
    Path standardUserHome = Files.createTempDirectory("seed4j-cli-apply-common-standard-");
    Path extensionUserHome = Files.createTempDirectory("seed4j-cli-apply-common-extension-");
    ExtensionRuntimeFixture.installWithApplyCommonSourceOverrideExtensionModule(extensionUserHome);
    Path standardProjectPath = Files.createTempDirectory("seed4j-cli-apply-common-standard-project-");
    Path extensionProjectPath = Files.createTempDirectory("seed4j-cli-apply-common-extension-project-");

    PackagedRunResult standardInitResult = runApplyInit(packagedCliJar, standardUserHome, standardProjectPath);
    PackagedRunResult extensionInitResult = runApplyInit(packagedCliJar, extensionUserHome, extensionProjectPath);
    PackagedRunResult standardPrettierResult = runApplyPrettier(packagedCliJar, standardUserHome, standardProjectPath);
    PackagedRunResult extensionPrettierResult = runApplyPrettier(packagedCliJar, extensionUserHome, extensionProjectPath);

    String standardPackageJson = Files.readString(standardProjectPath.resolve("package.json"));
    String extensionPackageJson = Files.readString(extensionProjectPath.resolve("package.json"));

    assertThat(standardInitResult.finished()).isTrue();
    assertThat(standardInitResult.exitCode()).isZero();
    assertThat(extensionInitResult.finished()).isTrue();
    assertThat(extensionInitResult.exitCode()).isZero();
    assertThat(standardPrettierResult.finished()).isTrue();
    assertThat(standardPrettierResult.exitCode()).isZero();
    assertThat(extensionPrettierResult.finished()).isTrue();
    assertThat(extensionPrettierResult.exitCode()).isZero();
    assertThat(standardPackageJson).doesNotContain("\"prettier\": \"" + OVERRIDDEN_PRETTIER_VERSION + "\"");
    assertThat(extensionPackageJson).contains("\"prettier\": \"" + OVERRIDDEN_PRETTIER_VERSION + "\"");
  }

  @Test
  void shouldOverrideCorePrettierTemplateWhenExtensionCollidesOnTheSameClasspathResourcePath() throws IOException, InterruptedException {
    Path packagedCliJar = packagedCliJar();
    Path standardUserHome = Files.createTempDirectory("seed4j-cli-apply-template-standard-");
    Path extensionUserHome = Files.createTempDirectory("seed4j-cli-apply-template-extension-");
    ExtensionRuntimeFixture.installWithApplyTemplateResourceOverrideExtensionModule(extensionUserHome);
    Path standardProjectPath = Files.createTempDirectory("seed4j-cli-apply-template-standard-project-");
    Path extensionProjectPath = Files.createTempDirectory("seed4j-cli-apply-template-extension-project-");

    PackagedRunResult standardInitResult = runApplyInit(packagedCliJar, standardUserHome, standardProjectPath);
    PackagedRunResult extensionInitResult = runApplyInit(packagedCliJar, extensionUserHome, extensionProjectPath);
    PackagedRunResult standardPrettierResult = runApplyPrettier(packagedCliJar, standardUserHome, standardProjectPath);
    PackagedRunResult extensionPrettierResult = runApplyPrettier(packagedCliJar, extensionUserHome, extensionProjectPath);

    String standardPrettierConfiguration = Files.readString(standardProjectPath.resolve(".prettierrc"));
    String extensionPrettierConfiguration = Files.readString(extensionProjectPath.resolve(".prettierrc"));

    assertThat(standardInitResult.finished()).isTrue();
    assertThat(standardInitResult.exitCode()).isZero();
    assertThat(extensionInitResult.finished()).isTrue();
    assertThat(extensionInitResult.exitCode()).isZero();
    assertThat(standardPrettierResult.finished()).isTrue();
    assertThat(standardPrettierResult.exitCode()).isZero();
    assertThat(extensionPrettierResult.finished()).isTrue();
    assertThat(extensionPrettierResult.exitCode()).isZero();
    assertThat(standardPrettierConfiguration).doesNotContain(OVERRIDDEN_PRETTIER_TEMPLATE_MARKER);
    assertThat(extensionPrettierConfiguration).contains(OVERRIDDEN_PRETTIER_TEMPLATE_MARKER);
  }

  // [TEST] should apply extension module using the same global runtime readers and resources

  private static PackagedRunResult runApplyInit(Path packagedCliJar, Path userHome, Path projectPath)
    throws IOException, InterruptedException {
    return runApply(packagedCliJar, userHome, projectPath, "init", "--node-package-manager", "npm");
  }

  private static PackagedRunResult runApplyPrettier(Path packagedCliJar, Path userHome, Path projectPath)
    throws IOException, InterruptedException {
    return runApply(packagedCliJar, userHome, projectPath, "prettier");
  }

  private static PackagedRunResult runApply(
    Path packagedCliJar,
    Path userHome,
    Path projectPath,
    String moduleSlug,
    String... additionalArguments
  ) throws IOException, InterruptedException {
    String[] commonArguments = {
      javaExecutablePath().toString(),
      "-Duser.home=" + userHome,
      "-jar",
      packagedCliJar.toString(),
      "apply",
      moduleSlug,
      "--project-path",
      projectPath.toString(),
      "--base-name",
      "sampleapp",
      "--project-name",
      "Sample App",
      "--no-commit",
    };
    String[] command = new String[commonArguments.length + additionalArguments.length];
    System.arraycopy(commonArguments, 0, command, 0, commonArguments.length);
    System.arraycopy(additionalArguments, 0, command, commonArguments.length, additionalArguments.length);
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
