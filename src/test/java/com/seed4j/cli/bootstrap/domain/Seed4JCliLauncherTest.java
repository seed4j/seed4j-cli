package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class Seed4JCliLauncherTest {

  private static final String MINIMAL_EXTENSION_METADATA = """
    distribution:
      id: company-extension
      version: 1.0.0
    """;

  @Test
  void shouldRunTheLocalCliPathWhenStandardModeIsSelectedOutsideARegularJar() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableLocation = Files.createTempDirectory("seed4j-cli-classes");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      executableLocation,
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isEqualTo(12);
    assertThat(localCliRunner.wasCalled()).isTrue();
    assertThat(childProcessLauncher.request()).isNull();
  }

  @Test
  void shouldFailBeforeSpringWhenExtensionModeIsSelectedOutsideARegularJar() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableLocation = Files.createTempDirectory("seed4j-cli-classes");
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
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      executableLocation,
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isNotZero();
    assertThat(localCliRunner.wasCalled()).isFalse();
    assertThat(childProcessLauncher.request()).isNull();
  }

  @Test
  void shouldWarnToStandardErrorBeforeRunningTheLocalCliPathWhenStandardModeIsSelectedOutsideARegularJar() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableLocation = Files.createTempDirectory("seed4j-cli-classes");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      executableLocation,
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = launcher.launch(new String[] { "--version" });

      assertThat(exitCode).isEqualTo(12);
      assertThat(localCliRunner.wasCalled()).isTrue();
      assertThat(childProcessLauncher.request()).isNull();
      assertThat(outputCaptor.getStandardError()).contains("not running from a packaged CLI JAR");
    }
  }

  @Test
  void shouldWarnAndRunTheLocalCliPathWhenStandardModeIsSelectedWithARegularFileThatIsNotAJar() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableLocation = Files.createTempFile("seed4j-cli-", ".bin");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      executableLocation,
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = launcher.launch(new String[] { "--version" });

      assertThat(exitCode).isEqualTo(12);
      assertThat(localCliRunner.wasCalled()).isTrue();
      assertThat(childProcessLauncher.request()).isNull();
      assertThat(outputCaptor.getStandardError()).contains("not running from a packaged CLI JAR");
    }
  }

  @Test
  void shouldStartAStandardChildProcessWhenNoExternalRuntimeConfigExists() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      createExecutableJar(),
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
      createExecutableJar(),
      "0.0.1-SNAPSHOT",
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" }, true);

    assertThat(exitCode).isEqualTo(12);
    assertThat(localCliRunner.wasCalled()).isTrue();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidRuntimeConfigurationContents")
  void shouldFailBeforeLaunchingAChildProcessWhenRuntimeConfigurationIsInvalid(String scenarioName, String configContent)
    throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, configContent);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      createExecutableJar(),
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
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      createExecutableJar(),
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

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("standardModeConfigurationContents")
  void shouldStartAStandardChildProcessWhenExternalConfigDoesNotSelectExtensionMode(String scenarioName, String configContent)
    throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, configContent);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      createExecutableJar(),
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
  void shouldFailBeforeLaunchingAChildProcessWhenExternalConfigCannotBeRead() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(configPath);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      createExecutableJar(),
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
    Path executableJar = createExecutableJar();
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
    Path executableJar = createExecutableJar();
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
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(userHome, executableJar, "0.0.1-SNAPSHOT", childProcessLauncher, localCliRunner);

    int exitCode = launcher.launch(new String[] { "--version" });

    Path extensionJarPath = runtimeDirectory.resolve("extension.jar");
    String extensionJarUri = extensionJarPath.toUri().toString();
    String expectedLoaderPath = "jar:" + extensionJarUri + "!/BOOT-INF/classes,jar:" + extensionJarUri + "!/BOOT-INF/lib/";

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().systemProperties())
      .containsEntry("seed4j.cli.runtime.child", "true")
      .containsEntry("seed4j.cli.runtime.mode", "extension")
      .containsEntry("seed4j.cli.runtime.distribution.id", "company-extension")
      .containsEntry("seed4j.cli.runtime.distribution.version", "1.0.0")
      .containsEntry("loader.path", expectedLoaderPath);
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  private static Stream<Arguments> invalidRuntimeConfigurationContents() {
    return Stream.of(
      Arguments.of(
        "extension mode selected without runtime artifacts",
        """
        seed4j:
          runtime:
            mode: extension
        """
      ),
      Arguments.of(
        "runtime mode has an invalid value",
        """
        seed4j:
          runtime:
            mode: corporate
        """
      ),
      Arguments.of(
        "external config root is not a map",
        """
        - seed4j
        - runtime
        """
      ),
      Arguments.of(
        "seed4j root is not a map",
        """
        seed4j: 123
        """
      ),
      Arguments.of(
        "runtime mode is not a string",
        """
        seed4j:
          runtime:
            mode:
              - standard
        """
      )
    );
  }

  private static Stream<Arguments> standardModeConfigurationContents() {
    return Stream.of(
      Arguments.of(
        "runtime mode explicitly set to standard",
        """
        seed4j:
          runtime:
            mode: standard
        """
      ),
      Arguments.of(
        "config file exists without runtime.mode",
        """
        seed4j:
          hidden-resources:
            slugs:
              - gradle-java
        """
      ),
      Arguments.of(
        "runtime section exists without mode",
        """
        seed4j:
          runtime:
            extension:
              fail-on-invalid: true
        """
      ),
      Arguments.of(
        "config file exists without seed4j section",
        """
        feature-flags:
          experimental: true
        """
      )
    );
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

  private static Path createExecutableJar() throws IOException {
    return Files.createTempFile("seed4j-cli-", ".jar");
  }
}
