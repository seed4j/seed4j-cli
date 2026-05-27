package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
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
import org.slf4j.LoggerFactory;

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
      userHome,
      executableJar,
      "0.0.1-SNAPSHOT",
      runtimeModeConfigurationRepository,
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

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
      userHome,
      executableJar,
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version", "--debug" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().systemProperties())
      .containsEntry("logging.config", "classpath:seed4j-cli-logback-spring.xml")
      .containsEntry("logging.level.com.seed4j.cli.bootstrap.domain", "DEBUG")
      .doesNotContainEntry("logging.level.root", "ERROR")
      .containsEntry("spring.main.log-startup-info", "false");
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
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      executableJar,
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );
    Logger logger = (Logger) LoggerFactory.getLogger(RuntimeExtensionLoaderPathResolver.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    int exitCode;
    try {
      exitCode = launcher.launch(new String[] { "--version", "--debug" });
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(exitCode).isZero();
    assertThat(appender.list)
      .extracting(ILoggingEvent::getFormattedMessage)
      .anyMatch(message -> message.contains("No extension runtime libraries were added to loader.path"));
  }

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
      new FileSystemRuntimeModeConfigurationRepository(userHome),
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
      userHome,
      executableLocation,
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
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
      new FileSystemRuntimeModeConfigurationRepository(userHome),
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
      new FileSystemRuntimeModeConfigurationRepository(userHome),
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
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

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
      userHome,
      createExecutableJar(),
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
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
  void shouldRunTheLocalCliPathWhenAlreadyExecutingAsAChildProcess() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      createExecutableJar(),
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
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
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, configContent);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      createExecutableJar(),
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
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
      userHome,
      createExecutableJar(),
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
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
      userHome,
      createExecutableJar(),
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = launcher.launch(new String[] { "--version" });

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
      userHome,
      createExecutableJar(),
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
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
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath);
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      createExecutableJar(),
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
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
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      executableJar,
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );

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
  void shouldNotPublishCliVersionSystemPropertyWhenBuildingTheChildProcessRequest() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      executableJar,
      "1.2.3",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().systemProperties()).doesNotContainKey("seed4j.cli.version");
  }

  @Test
  void shouldPublishSeed4jVersionSystemPropertyWhenBuildingTheChildProcessRequest() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createExecutableJar();
    RecordingChildProcessLauncher childProcessLauncher = new RecordingChildProcessLauncher();
    RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHome,
      executableJar,
      "9.8.7",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().systemProperties()).containsEntry("seed4j.cli.seed4j.version", "9.8.7");
  }

  @Test
  void shouldLaunchTheExtensionChildProcessRequestWithLoaderPathAndActiveDistributionSystemProperties() throws IOException {
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
      userHome,
      executableJar,
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    Path extensionJarPath = runtimeDirectory.resolve("extension.jar");
    RuntimeExtensionCacheIdentity cacheIdentity = new RuntimeExtensionCacheIdentityResolver().resolve(extensionJarPath);
    Path overlayClassesPath = userHome.resolve(".config/seed4j-cli/runtime/cache").resolve(cacheIdentity.value()).resolve("classes");
    String expectedLoaderPath = overlayClassesPath.toString();

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

  @Test
  void shouldSetExtensionChildProcessLoggingBaselineSystemPropertiesWhenExtensionModeIsSelected() throws IOException {
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
      userHome,
      executableJar,
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().systemProperties())
      .containsEntry("logging.config", "classpath:seed4j-cli-logback-spring.xml")
      .containsEntry("logging.level.root", "ERROR")
      .containsEntry("spring.main.log-startup-info", "false");
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
      userHome,
      executableJar,
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    RuntimeExtensionCacheIdentity cacheIdentity = new RuntimeExtensionCacheIdentityResolver().resolve(extensionJarPath);
    Path overlayClassesPath = userHome.resolve(".config/seed4j-cli/runtime/cache").resolve(cacheIdentity.value()).resolve("classes");
    assertThat(exitCode).isZero();
    assertThat(overlayClassesPath).exists().isDirectory();
  }

  @Test
  void shouldPublishExtensionStartClassSystemPropertyWhenExtensionModeIsSelected() throws IOException {
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
      userHome,
      executableJar,
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );

    int exitCode = launcher.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(childProcessLauncher.request()).isNotNull();
    assertThat(childProcessLauncher.request().systemProperties()).containsEntry(
      "seed4j.cli.runtime.extension.start-class",
      "com.seed4j.extension.ExtensionApplication"
    );
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
      userHome,
      executableJar,
      "0.0.1-SNAPSHOT",
      new FileSystemRuntimeModeConfigurationRepository(userHome),
      childProcessLauncher,
      localCliRunner
    );

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = launcher.launch(new String[] { "--version" });

      assertThat(exitCode).isNotZero();
      assertThat(childProcessLauncher.request()).isNull();
      assertThat(localCliRunner.wasCalled()).isFalse();
      assertThat(outputCaptor.getStandardError()).contains("Start-Class");
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

  private static Path createFatJarWithoutStartClass(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static Path createFlatJar(Path jarPath) throws IOException {
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
    return jarPath;
  }
}
