package com.seed4j.cli.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliLauncherTest {

  @Test
  void shouldStartAStandardChildProcessWhenNoExternalRuntimeConfigExists() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
  }

  @Test
  void shouldRunTheLocalCliPathWhenAlreadyExecutingAsAChildProcess() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" }, true);

    assertThat(exitCode).isEqualTo(12);
    assertThat(localCliRunner.wasCalled()).isTrue();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
  }

  @Test
  void shouldFailBeforeLaunchingAChildProcessWhenExtensionRuntimeConfigurationIsInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: extension
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isNotZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldStartAChildProcessWhenExtensionRuntimeConfigurationIsValid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Path runtimeDirectory = userHome.resolve(".config/seed4j-cli/runtime/active");
    Files.createDirectories(configPath.getParent());
    Files.createDirectories(runtimeDirectory);
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: extension
      """
    );
    Files.createFile(runtimeDirectory.resolve("extension.jar"));
    Files.writeString(
      runtimeDirectory.resolve("metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
        kind: extension
        vendor: acme
      artifact:
        filename: extension.jar
      compatibility:
        cli: 0.0.1
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNotNull();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(childProcessLauncher.runtimeSelection().distributionId()).contains("company-extension");
    assertThat(childProcessLauncher.runtimeSelection().distributionVersion()).contains("1.0.0");
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldStartAStandardChildProcessWhenRuntimeModeIsExplicitlyStandardInTheExternalConfig() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNotNull();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldStartAStandardChildProcessWhenTheExternalConfigExistsButRuntimeModeIsNotDeclared() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        hidden-resources:
          slugs:
            - gradle-java
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNotNull();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldStartAStandardChildProcessWhenRuntimeConfigExistsButModeIsNotDeclared() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          extension:
            fail-on-invalid: true
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNotNull();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldStartAStandardChildProcessWhenTheExternalConfigExistsButSeed4jIsNotDeclared() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      feature-flags:
        experimental: true
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNotNull();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldFailBeforeLaunchingAChildProcessWhenRuntimeModeHasAnInvalidValue() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: corporate
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isNotZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldFailBeforeLaunchingAChildProcessWhenExternalConfigYamlRootIsInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      - seed4j
      - runtime
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isNotZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldFailBeforeLaunchingAChildProcessWhenSeed4jConfigHasAnInvalidType() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j: 123
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isNotZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldFailBeforeLaunchingAChildProcessWhenRuntimeModeHasAnInvalidType() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode:
            - standard
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isNotZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldFailBeforeLaunchingAChildProcessWhenExternalConfigCannotBeRead() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      Path.of("/tmp/seed4j-cli.jar"),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isNotZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldLaunchTheStandardChildProcessThroughJavaPropertiesLauncherWithChildModeSystemProperties() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = Path.of("/tmp/seed4j-cli.jar");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(userHome, executableJar, "0.0.1-SNAPSHOT", childProcessLauncher, localCliRunner);

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().executableJar()).isEqualTo(executableJar);
    assertThat(childProcessLauncher.request().mainClass()).isEqualTo("org.springframework.boot.loader.launch.PropertiesLauncher");
    assertThat(childProcessLauncher.request().systemProperties())
      .containsEntry("seed4j.cli.runtime.child", "true")
      .containsEntry("seed4j.cli.runtime.mode", "standard");
    assertThat(childProcessLauncher.request().arguments()).containsExactly("--version");
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldLaunchTheExtensionChildProcessRequestWithLoaderPathAndActiveDistributionSystemProperties() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = Path.of("/tmp/seed4j-cli.jar");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Path runtimeDirectory = userHome.resolve(".config/seed4j-cli/runtime/active");
    Files.createDirectories(configPath.getParent());
    Files.createDirectories(runtimeDirectory);
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: extension
      """
    );
    Files.createFile(runtimeDirectory.resolve("extension.jar"));
    Files.writeString(
      runtimeDirectory.resolve("metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
        kind: extension
        vendor: acme
      artifact:
        filename: extension.jar
      compatibility:
        cli: 0.0.1
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(userHome, executableJar, "0.0.1-SNAPSHOT", childProcessLauncher, localCliRunner);

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().systemProperties())
      .containsEntry("seed4j.cli.runtime.child", "true")
      .containsEntry("seed4j.cli.runtime.mode", "extension")
      .containsEntry("seed4j.cli.runtime.distribution.id", "company-extension")
      .containsEntry("seed4j.cli.runtime.distribution.version", "1.0.0")
      .containsEntry("loader.path", runtimeDirectory.resolve("extension.jar").toString());
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  private static final class RecordingChildProcessLauncher implements ChildProcessLauncher {

    private JavaChildProcessRequest request;

    @Override
    public int launch(JavaChildProcessRequest request) {
      this.request = request;
      return 0;
    }

    RuntimeSelection runtimeSelection() {
      if (request == null) {
        return null;
      }

      return request.runtimeSelection();
    }

    JavaChildProcessRequest request() {
      return request;
    }
  }

  private static final class RecordingLocalCliRunner implements LocalCliRunner {

    private boolean called;

    @Override
    public int run(String[] args) {
      called = true;
      return 12;
    }

    boolean wasCalled() {
      return called;
    }
  }
}
