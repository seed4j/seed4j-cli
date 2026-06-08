package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionSelectionRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JarRuntimeExtensionPackageValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
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
  void shouldReadRuntimeModeFromInjectedRuntimeModeConfigurationRepository() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    RecordingRuntimeModeConfigurationRepository runtimeModeConfigurationRepository = new RecordingRuntimeModeConfigurationRepository(
      userHome.resolve(".config/seed4j-cli/config.yml"),
      RuntimeMode.STANDARD
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableJar, false),
      runtimeModeConfigurationRepository,
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isZero();
    assertThat(runtimeModeConfigurationRepository.readModeCalls()).isEqualTo(1);
    assertThat(runtimeModeConfigurationRepository.readConfigurationCalls()).isZero();
    assertThat(runtimeModeConfigurationRepository.prepareModeChangeCalls()).isZero();
    assertThat(runtimeModeConfigurationRepository.persistModeCalls()).isZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNotNull();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldNotForceErrorRootLoggingWhenDebugFlagIsPresentInExtensionMode() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
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
    createFatJar(runtimeDirectory.resolve("extension.jar"));
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableJar, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version", "--debug"));

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().debug()).isTrue();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldEmitBootstrapDiagnosticsInParentProcessWhenDebugFlagIsPresentInExtensionMode() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
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
    createFatJar(runtimeDirectory.resolve("extension.jar"));
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    RecordingBootstrapDiagnostics bootstrapDiagnostics = new RecordingBootstrapDiagnostics();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableJar, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      executablePath -> true,
      bootstrapDiagnostics,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version", "--debug"));

    assertThat(exitCode).isZero();
    assertThat(bootstrapDiagnostics.enableDebugLoggingCalls()).isEqualTo(1);
  }

  @Test
  void shouldRunTheLocalCliPathWhenStandardModeIsSelectedOutsideARegularJar() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableLocation = Files.createTempDirectory("seed4j-cli-classes");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableLocation, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isEqualTo(12);
    assertThat(localCliRunner.wasCalled()).isTrue();
    assertThat(childProcessLauncher.request()).isNull();
  }

  @Test
  void shouldFailBeforeSpringWhenExtensionModeIsSelectedOutsideARegularJar() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableLocation = Files.createTempDirectory("seed4j-cli-classes");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
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
    createFatJar(runtimeDirectory.resolve("extension.jar"));
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableLocation, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

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
      seed4jCliRuntime(executableLocation, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = launcher.launch(arguments("--version"));

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
      seed4jCliRuntime(executableLocation, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = launcher.launch(arguments("--version"));

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
      seed4jCliRuntime(createExecutableJar(), false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
  }

  @Test
  void shouldIgnoreLegacyExternalRuntimeConfigPath() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path legacyConfigPath = userHome.resolve(".config/seed4j-cli.yml");
    Files.createDirectories(legacyConfigPath.getParent());
    Files.writeString(
      legacyConfigPath,
      """
      seed4j:
        runtime:
          mode: extension
      """
    );
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(createExecutableJar(), false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNotNull();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldRunTheLocalCliPathWhenAlreadyExecutingAsAChildProcess() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(createExecutableJar(), true),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isEqualTo(12);
    assertThat(localCliRunner.wasCalled()).isTrue();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidRuntimeConfigurationContents")
  void shouldFailBeforeLaunchingAChildProcessWhenRuntimeConfigurationIsInvalid(String scenarioName, String configContent)
    throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, configContent);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(createExecutableJar(), false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isNotZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldStartAChildProcessWhenExtensionRuntimeConfigurationIsValid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
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
    createFatJar(runtimeDirectory.resolve("extension.jar"));
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(createExecutableJar(), false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNotNull();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(childProcessLauncher.runtimeSelection().distributionId()).contains(new RuntimeDistributionId("company-extension"));
    assertThat(childProcessLauncher.runtimeSelection().distributionVersion()).contains(new RuntimeDistributionVersion("1.0.0"));
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldFailBeforeChildProcessAndPrintRuntimeErrorWhenExtensionJarLayoutIsInvalid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
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
    createFlatJar(runtimeDirectory.resolve("extension.jar"));
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(createExecutableJar(), false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = launcher.launch(arguments("--version"));

      assertThat(exitCode).isNotZero();
      assertThat(childProcessLauncher.request()).isNull();
      assertThat(localCliRunner.wasCalled()).isFalse();
      assertThat(outputCaptor.getStandardError()).contains("BOOT-INF/classes");
    }
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("standardModeConfigurationContents")
  void shouldStartAStandardChildProcessWhenExternalConfigDoesNotSelectExtensionMode(String scenarioName, String configContent)
    throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, configContent);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(createExecutableJar(), false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNotNull();
    assertThat(childProcessLauncher.runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldFailBeforeLaunchingAChildProcessWhenExternalConfigCannotBeRead() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(createExecutableJar(), false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isNotZero();
    assertThat(childProcessLauncher.runtimeSelection()).isNull();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldLaunchTheStandardChildRuntimeWithRuntimeSelectionAndArguments() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableJar, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().executableJar()).isEqualTo(executableJar);
    assertThat(childProcessLauncher.request().runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(childProcessLauncher.request().arguments().asList()).containsExactly("--version");
    assertThat(childProcessLauncher.request().debug()).isFalse();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldLaunchTheStandardChildRuntimeWithoutDedicatedVersionFields() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableJar, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().runtimeSelection().distributionId()).isEmpty();
    assertThat(childProcessLauncher.request().runtimeSelection().distributionVersion()).isEmpty();
  }

  @Test
  void shouldLaunchTheExtensionChildRuntimeWithActiveDistributionSelection() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
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
    createFatJar(runtimeDirectory.resolve("extension.jar"));
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableJar, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().runtimeSelection().mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(childProcessLauncher.request().runtimeSelection().distributionId()).contains(new RuntimeDistributionId("company-extension"));
    assertThat(childProcessLauncher.request().runtimeSelection().distributionVersion()).contains(new RuntimeDistributionVersion("1.0.0"));
    assertThat(childProcessLauncher.request().runtimeSelection().extensionJarPath()).contains(
      new RuntimeExtensionJarPath(runtimeDirectory.resolve("extension.jar"))
    );
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldLaunchTheExtensionChildRuntimeWithoutDebugFlagWhenDebugIsAbsent() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
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
    createFatJar(runtimeDirectory.resolve("extension.jar"));
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableJar, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().runtimeSelection().mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(childProcessLauncher.request().debug()).isFalse();
    assertThat(localCliRunner.wasCalled()).isFalse();
  }

  @Test
  void shouldMaterializeExtensionOverlayCacheWhenExtensionModeIsSelected() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
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
    Path extensionJarPath = createFatJar(runtimeDirectory.resolve("extension.jar"));
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableJar, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request().runtimeSelection().extensionJarPath()).contains(
      new RuntimeExtensionJarPath(extensionJarPath)
    );
  }

  @Test
  void shouldLaunchTheExtensionChildRuntimeWhenExtensionStartClassIsPresent() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
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
    createFatJarWithStartClass(runtimeDirectory.resolve("extension.jar"), "com.seed4j.extension.ExtensionApplication");
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableJar, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    int exitCode = launcher.launch(arguments("--version"));

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().runtimeSelection().mode()).isEqualTo(RuntimeMode.EXTENSION);
  }

  @Test
  void shouldFailBeforeChildProcessWhenExtensionStartClassIsMissingFromManifest() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
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
    createFatJarWithoutStartClass(runtimeDirectory.resolve("extension.jar"));
    Files.writeString(runtimeDirectory.resolve("metadata.yml"), MINIMAL_EXTENSION_METADATA);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      seed4jCliRuntime(executableJar, false),
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      runtimeExtensionSelectionRepository(userHome),
      childProcessLauncher,
      localCliRunner,
      new com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector(),
      () -> {},
      new com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput()
    );

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = launcher.launch(arguments("--version"));

      assertThat(exitCode).isZero();
      assertThat(childProcessLauncher.request()).isNotNull();
      assertThat(localCliRunner.wasCalled()).isFalse();
      assertThat(outputCaptor.getStandardError()).isEmpty();
    }
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

  private static RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository(Path userHome) {
    return new FileSystemRuntimeExtensionSelectionRepository(new Seed4JCliHome(userHome), new JarRuntimeExtensionPackageValidator());
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

  private static final class RecordingRuntimeModeConfigurationRepository implements RuntimeModeConfigurationRepository {

    private final Path configPath;
    private final RuntimeMode runtimeMode;
    private int readModeCalls;
    private int readConfigurationCalls;
    private int prepareModeChangeCalls;
    private int persistModeCalls;

    private RecordingRuntimeModeConfigurationRepository(Path configPath, RuntimeMode runtimeMode) {
      this.configPath = configPath;
      this.runtimeMode = runtimeMode;
    }

    @Override
    public RuntimeMode readMode() {
      readModeCalls = readModeCalls + 1;
      return runtimeMode;
    }

    @Override
    public RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
      prepareModeChangeCalls = prepareModeChangeCalls + 1;
      readConfigurationCalls = readConfigurationCalls + 1;

      return new RuntimeModeChangePlan() {
        @Override
        public Path configPath() {
          return configPath;
        }

        @Override
        public void apply() {
          persistModeCalls = persistModeCalls + 1;
        }
      };
    }

    private int readModeCalls() {
      return readModeCalls;
    }

    private int readConfigurationCalls() {
      return readConfigurationCalls;
    }

    private int prepareModeChangeCalls() {
      return prepareModeChangeCalls;
    }

    private int persistModeCalls() {
      return persistModeCalls;
    }
  }

  private static final class RecordingChildProcessLauncher implements ChildRuntimeLauncher {

    private ChildRuntimeLaunchRequest request;

    @Override
    public int launch(ChildRuntimeLaunchRequest request) {
      this.request = request;
      return 0;
    }

    RuntimeSelection runtimeSelection() {
      if (request == null) {
        return null;
      }

      return request.runtimeSelection();
    }

    ChildRuntimeLaunchRequest request() {
      return request;
    }
  }

  private static final class RecordingLocalCliRunner implements LocalCliRunner {

    private boolean called;

    @Override
    public int run(Seed4JCliArguments arguments) {
      called = true;
      return 12;
    }

    boolean wasCalled() {
      return called;
    }
  }

  private static final class RecordingBootstrapDiagnostics implements BootstrapDiagnostics {

    private int enableDebugLoggingCalls;

    @Override
    public void enableDebugLogging() {
      enableDebugLoggingCalls = enableDebugLoggingCalls + 1;
    }

    int enableDebugLoggingCalls() {
      return enableDebugLoggingCalls;
    }
  }

  private static Path createExecutableJar() throws IOException {
    return Files.createTempFile("seed4j-cli-", ".jar");
  }

  private static Seed4JCliRuntime seed4jCliRuntime(Path executableJar, boolean childRuntime) {
    return new Seed4JCliRuntime() {
      @Override
      public Path executableJar() {
        return executableJar;
      }

      @Override
      public boolean childRuntime() {
        return childRuntime;
      }
    };
  }

  private static Seed4JCliArguments arguments(String... values) {
    return new Seed4JCliArguments(values);
  }

  private static Path createFatJar(Path jarPath) throws IOException {
    return createFatJarWithStartClass(jarPath, "com.seed4j.extension.ExtensionApplication");
  }

  private static Path createFatJarWithStartClass(Path jarPath, String startClass) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Start-Class", startClass);
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/com/seed4j/extension/ExtensionApplication.class"));
      jarOutputStream.write(new byte[] { 0 });
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static void createFatJarWithoutStartClass(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
    }
  }

  private static void createFlatJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("com/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("com/company/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("com/company/Extension.class"));
      jarOutputStream.write(new byte[] { 0 });
      jarOutputStream.closeEntry();
    }
  }
}
