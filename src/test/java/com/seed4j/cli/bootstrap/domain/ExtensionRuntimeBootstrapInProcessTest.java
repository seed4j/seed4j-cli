package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionSelectionRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JarRuntimeExtensionPackageValidator;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SpringBootLocalCliRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

@UnitTest
class ExtensionRuntimeBootstrapInProcessTest {

  private static final String RUNTIME_MODE_PROPERTY = "seed4j.cli.runtime.mode";
  private static final String DISTRIBUTION_ID_PROPERTY = "seed4j.cli.runtime.distribution.id";
  private static final String DISTRIBUTION_VERSION_PROPERTY = "seed4j.cli.runtime.distribution.version";
  private static final String LOADER_PATH_PROPERTY = "loader.path";
  private static final String BASELINE_RUNTIME_MODE = "baseline-mode";
  private static final String EXTENSION_ONLY_SLUG = "runtime-extension-list-only";
  private static final String CORE_SLUG_THAT_EXTENSION_TRIES_TO_HIDE = "gradle-java";
  private static final String SPRING_BOOT_BANNER_MARKER = " :: Spring Boot :: ";
  private static final String STARTUP_INFO_MARKER = "Starting Seed4JCliApp";
  private static final String EXTENSION_LOGBACK_OVERRIDE_MARKER = "[EXT-LOGBACK-OVERRIDE]";
  private static final String EXTENSION_APPLICATION_OVERRIDE_MARKER = "[EXT-APPLICATION-OVERRIDE]";
  private static final String EXTENSION_APPLICATION_YML_ENTRY = "BOOT-INF/classes/config/application.yml";
  private static final String EXTENSION_LOGBACK_ENTRY = "BOOT-INF/classes/logback-spring.xml";

  @Test
  void shouldExecuteVersionCommandInExtensionModeUsingInProcessChildLauncher() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    ExtensionRuntimeFixture.install(userHome);
    Path executableJar = Files.createTempFile("seed4j-cli-", ".jar");
    LocalCliRunner localCliRunner = localCliRunner(userHome);
    InProcessChildProcessLauncher childProcessLauncher = new InProcessChildProcessLauncher(new Seed4JCliHome(userHome), localCliRunner);
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
    ScopedSystemProperties baselineProperties = ScopedSystemProperties.capture(
      Set.of(RUNTIME_MODE_PROPERTY, DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY)
    );

    try {
      System.setProperty(RUNTIME_MODE_PROPERTY, BASELINE_RUNTIME_MODE);
      System.clearProperty(DISTRIBUTION_ID_PROPERTY);
      System.clearProperty(DISTRIBUTION_VERSION_PROPERTY);
      System.clearProperty(LOADER_PATH_PROPERTY);

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        int exitCode = launcher.launch(arguments("--version"));

        assertThat(exitCode).isZero();
        assertThat(outputCaptor.getOutput())
          .contains("Runtime mode: extension")
          .contains("Distribution ID: company-extension")
          .contains("Distribution version: 1.0.0");
      }

      assertThat(System.getProperty(RUNTIME_MODE_PROPERTY)).isEqualTo(BASELINE_RUNTIME_MODE);
      assertThat(System.getProperty(DISTRIBUTION_ID_PROPERTY)).isNull();
      assertThat(System.getProperty(DISTRIBUTION_VERSION_PROPERTY)).isNull();
      assertThat(System.getProperty(LOADER_PATH_PROPERTY)).isNull();
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldListExtensionOnlyModuleWhenRunningInExtensionModeUsingInProcessChildLauncher() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-list-");
    ExtensionRuntimeFixture.installWithListExtensionModule(userHome);
    Path executableJar = Files.createTempFile("seed4j-cli-", ".jar");
    LocalCliRunner localCliRunner = localCliRunner(userHome);
    InProcessChildProcessLauncher childProcessLauncher = new InProcessChildProcessLauncher(new Seed4JCliHome(userHome), localCliRunner);
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
    ScopedSystemProperties baselineProperties = ScopedSystemProperties.capture(
      Set.of(RUNTIME_MODE_PROPERTY, DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY)
    );

    try {
      System.setProperty(RUNTIME_MODE_PROPERTY, BASELINE_RUNTIME_MODE);
      System.clearProperty(DISTRIBUTION_ID_PROPERTY);
      System.clearProperty(DISTRIBUTION_VERSION_PROPERTY);
      System.clearProperty(LOADER_PATH_PROPERTY);

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        int exitCode = launcher.launch(arguments("list"));

        assertThat(exitCode).isZero();
        assertThat(outputCaptor.getOutput()).contains(EXTENSION_ONLY_SLUG);
      }

      assertThat(System.getProperty(RUNTIME_MODE_PROPERTY)).isEqualTo(BASELINE_RUNTIME_MODE);
      assertThat(System.getProperty(DISTRIBUTION_ID_PROPERTY)).isNull();
      assertThat(System.getProperty(DISTRIBUTION_VERSION_PROPERTY)).isNull();
      assertThat(System.getProperty(LOADER_PATH_PROPERTY)).isNull();
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldKeepCoreModulesVisibleWhenExtensionPublishesHiddenResourcesOverrides() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-hidden-resources-");
    ExtensionRuntimeFixture.ExtensionRuntimeFixturePaths fixturePaths =
      ExtensionRuntimeFixture.installWithListExtensionModuleAndHiddenResourcesOverrides(userHome);
    Path executableJar = Files.createTempFile("seed4j-cli-", ".jar");
    LocalCliRunner localCliRunner = localCliRunner(userHome);
    InProcessChildProcessLauncher childProcessLauncher = new InProcessChildProcessLauncher(new Seed4JCliHome(userHome), localCliRunner);
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
    assertThat(jarEntries(fixturePaths.extensionJarPath())).contains(EXTENSION_APPLICATION_YML_ENTRY);
    ScopedSystemProperties baselineProperties = ScopedSystemProperties.capture(
      Set.of(RUNTIME_MODE_PROPERTY, DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY)
    );

    try {
      System.setProperty(RUNTIME_MODE_PROPERTY, BASELINE_RUNTIME_MODE);
      System.clearProperty(DISTRIBUTION_ID_PROPERTY);
      System.clearProperty(DISTRIBUTION_VERSION_PROPERTY);
      System.clearProperty(LOADER_PATH_PROPERTY);

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        int exitCode = launcher.launch(arguments("list"));

        assertThat(exitCode).isZero();
        assertThat(outputCaptor.getOutput()).contains(EXTENSION_ONLY_SLUG).contains(CORE_SLUG_THAT_EXTENSION_TRIES_TO_HIDE);
      }
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldKeepOperationalOutputCleanWhenExtensionJarPublishesLoggingOverrides() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-logging-");
    ExtensionRuntimeFixture.ExtensionRuntimeFixturePaths fixturePaths =
      ExtensionRuntimeFixture.installWithListExtensionModuleAndLoggingOverrides(userHome);
    Path executableJar = Files.createTempFile("seed4j-cli-", ".jar");
    LocalCliRunner localCliRunner = localCliRunner(userHome);
    InProcessChildProcessLauncher childProcessLauncher = new InProcessChildProcessLauncher(new Seed4JCliHome(userHome), localCliRunner);
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
    assertThat(jarEntries(fixturePaths.extensionJarPath())).contains(EXTENSION_APPLICATION_YML_ENTRY, EXTENSION_LOGBACK_ENTRY);
    ScopedSystemProperties baselineProperties = ScopedSystemProperties.capture(
      Set.of(RUNTIME_MODE_PROPERTY, DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY)
    );

    try {
      System.setProperty(RUNTIME_MODE_PROPERTY, BASELINE_RUNTIME_MODE);
      System.clearProperty(DISTRIBUTION_ID_PROPERTY);
      System.clearProperty(DISTRIBUTION_VERSION_PROPERTY);
      System.clearProperty(LOADER_PATH_PROPERTY);

      CliLaunchResult versionLaunch = launchCapturingOutput(launcher, "--version");
      assertSuccessfulLaunch("--version before list", versionLaunch);
      assertThat(versionLaunch.output())
        .contains("Runtime mode: extension")
        .contains("Distribution ID: company-extension")
        .contains("Distribution version: 1.0.0")
        .doesNotContain(SPRING_BOOT_BANNER_MARKER)
        .doesNotContain(STARTUP_INFO_MARKER)
        .doesNotContain(EXTENSION_LOGBACK_OVERRIDE_MARKER)
        .doesNotContain(EXTENSION_APPLICATION_OVERRIDE_MARKER);

      CliLaunchResult listLaunch = launchCapturingOutput(launcher, "list");
      assertSuccessfulLaunch("list after --version", listLaunch);
      assertThat(listLaunch.output())
        .contains(EXTENSION_ONLY_SLUG)
        .doesNotContain(SPRING_BOOT_BANNER_MARKER)
        .doesNotContain(STARTUP_INFO_MARKER)
        .doesNotContain(EXTENSION_LOGBACK_OVERRIDE_MARKER)
        .doesNotContain(EXTENSION_APPLICATION_OVERRIDE_MARKER);
    } finally {
      baselineProperties.restore();
    }
  }

  private static CliLaunchResult launchCapturingOutput(Seed4JCliLauncher launcher, String... arguments) {
    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = launcher.launch(new Seed4JCliArguments(arguments));
      return new CliLaunchResult(exitCode, outputCaptor.getOutput());
    }
  }

  private static void assertSuccessfulLaunch(String launchName, CliLaunchResult launchResult) {
    assertThat(launchResult.exitCode()).as("%s should exit with code 0. Captured output:%n%s", launchName, launchResult.output()).isZero();
  }

  private static List<String> jarEntries(Path extensionJarPath) throws IOException {
    try (JarFile extensionJarFile = new JarFile(extensionJarPath.toFile())) {
      return extensionJarFile.stream().map(JarEntry::getName).toList();
    }
  }

  private static LocalCliRunner localCliRunner(Path userHome) {
    return new SpringBootLocalCliRunner(Seed4JCliApp.class, new Seed4JCliHome(userHome));
  }

  private static RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository(Path userHome) {
    return new FileSystemRuntimeExtensionSelectionRepository(new Seed4JCliHome(userHome), new JarRuntimeExtensionPackageValidator());
  }

  private static final class InProcessChildProcessLauncher implements ChildRuntimeLauncher {

    private final LocalCliRunner localCliRunner;
    private final Seed4JCliHome cliHome;

    private InProcessChildProcessLauncher(Seed4JCliHome cliHome, LocalCliRunner localCliRunner) {
      this.cliHome = cliHome;
      this.localCliRunner = localCliRunner;
    }

    @Override
    public int launch(ChildRuntimeLaunchRequest request) {
      Map<String, String> systemProperties = systemProperties(request);
      ScopedSystemProperties scopedSystemProperties = ScopedSystemProperties.capture(systemProperties.keySet());
      try {
        systemProperties.forEach(System::setProperty);
        return localCliRunner.run(request.arguments());
      } finally {
        scopedSystemProperties.restore();
      }
    }

    private Map<String, String> systemProperties(ChildRuntimeLaunchRequest request) {
      RuntimeSelection runtimeSelection = request.runtimeSelection();
      Map<String, String> systemProperties = new LinkedHashMap<>();
      systemProperties.put("seed4j.cli.runtime.child", "true");
      systemProperties.put(RUNTIME_MODE_PROPERTY, runtimeSelection.mode().name().toLowerCase());
      runtimeSelection.distributionId().ifPresent(distributionId -> systemProperties.put(DISTRIBUTION_ID_PROPERTY, distributionId.id()));
      runtimeSelection
        .distributionVersion()
        .ifPresent(distributionVersion -> systemProperties.put(DISTRIBUTION_VERSION_PROPERTY, distributionVersion.version()));
      runtimeSelection
        .extensionJarPath()
        .ifPresent(extensionJarPath -> {
          Path rawExtensionJarPath = extensionJarPath.path();
          systemProperties.put(
            "seed4j.cli.runtime.extension.start-class",
            new com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionStartClassResolver().resolve(rawExtensionJarPath)
          );
          Path overlayClassesPath = new com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionOverlayCache(cliHome).materialize(
            rawExtensionJarPath
          );
          systemProperties.put(
            LOADER_PATH_PROPERTY,
            new com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionLoaderPathResolver().resolve(
              overlayClassesPath,
              rawExtensionJarPath,
              request.executableJar()
            )
          );
        });
      systemProperties.put("logging.config", "classpath:seed4j-cli-logback-spring.xml");
      systemProperties.put("logging.level.root", "ERROR");
      systemProperties.put("spring.main.log-startup-info", "false");
      return Map.copyOf(systemProperties);
    }
  }

  private static Seed4JCliArguments arguments(String... values) {
    return new Seed4JCliArguments(values);
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

  private record CliLaunchResult(int exitCode, String output) {}

  private record ScopedSystemProperties(Map<String, Optional<String>> originalValues) {
    private static ScopedSystemProperties capture(Set<String> propertyKeys) {
      Map<String, Optional<String>> capturedValues = new LinkedHashMap<>();
      for (String propertyKey : propertyKeys) {
        capturedValues.put(propertyKey, Optional.ofNullable(System.getProperty(propertyKey)));
      }
      return new ScopedSystemProperties(Map.copyOf(capturedValues));
    }

    private void restore() {
      originalValues.forEach((propertyKey, propertyValue) -> {
        if (propertyValue.isPresent()) {
          System.setProperty(propertyKey, propertyValue.orElseThrow());
        } else {
          System.clearProperty(propertyKey);
        }
      });
    }
  }
}
