package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.domain.BootstrapDiagnostics;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLauncher;
import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture.ExtensionRuntimeFixturePaths;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionSelectionRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JarRuntimeExtensionPackageValidator;
import com.seed4j.cli.bootstrap.infrastructure.secondary.PreSpringRuntimeEnvironmentSeed4JCliRuntime;
import com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionLoaderPathResolver;
import com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionOverlayCache;
import com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionStartClassResolver;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SpringBootLocalCliRunner;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@UnitTest
class PreSpringBootstrapPrimaryTest {

  private static final String RUNTIME_MODE_PROPERTY = "seed4j.cli.runtime.mode";
  private static final String DISTRIBUTION_ID_PROPERTY = "seed4j.cli.runtime.distribution.id";
  private static final String DISTRIBUTION_VERSION_PROPERTY = "seed4j.cli.runtime.distribution.version";
  private static final String LOADER_PATH_PROPERTY = "loader.path";
  private static final String BASELINE_RUNTIME_MODE = "baseline-mode";
  private static final String EXTENSION_ONLY_SLUG = "runtime-extension-list-only";
  private static final String CUSTOM_PACKAGE_EXTENSION_ONLY_SLUG = "runtime-extension-custom-package-list-only";
  private static final String CORE_SLUG_THAT_EXTENSION_TRIES_TO_HIDE = "gradle-java";
  private static final String EXTENSION_SHARED_RUNTIME_APPLY_MODULE_SLUG = "runtime-extension-apply-shared-context";
  private static final String OVERRIDDEN_PRETTIER_VERSION = "3.6.2";
  private static final String OVERRIDDEN_PRETTIER_TEMPLATE_MARKER = "seed4j-extension-template-override";
  private static final String SPRING_BOOT_BANNER_MARKER = " :: Spring Boot :: ";
  private static final String STARTUP_INFO_MARKER = "Starting Seed4JCliApp";
  private static final String EXTENSION_LOGBACK_OVERRIDE_MARKER = "[EXT-LOGBACK-OVERRIDE]";
  private static final String EXTENSION_APPLICATION_OVERRIDE_MARKER = "[EXT-APPLICATION-OVERRIDE]";
  private static final Pattern MODULE_LINE_PATTERN = Pattern.compile("^\\s{2}(\\S+)\\s{2,}.+$");
  private static final Pattern MODULE_SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

  @Test
  void shouldExecuteVersionCommandInExtensionMode() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-version-primary-");
    ExtensionRuntimeFixture.install(userHome);
    PreSpringBootstrapRunner runner = runner(userHome);
    ScopedSystemProperties baselineProperties = capturedRuntimeProperties();

    try {
      setBaselineRuntimeProperties();

      CliLaunchResult versionLaunch = launchCapturingOutput(runner, "--version");

      assertThat(versionLaunch.exitCode()).isZero();
      assertThat(versionLaunch.output())
        .contains("Runtime mode: extension")
        .contains("Distribution ID: company-extension")
        .contains("Distribution version: 1.0.0");
      assertBaselineRuntimePropertiesRestored();
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldKeepStandardCatalogAndAddOnlyTheExtensionOnlySlug() throws IOException {
    Path standardUserHome = Files.createTempDirectory("seed4j-cli-standard-catalog-primary-");
    Path extensionUserHome = Files.createTempDirectory("seed4j-cli-extension-catalog-primary-");
    ExtensionRuntimeFixture.installWithListExtensionModule(extensionUserHome);
    ScopedSystemProperties baselineProperties = capturedRuntimeProperties();

    try {
      setBaselineRuntimeProperties();

      CliLaunchResult standardResult = launchCapturingOutput(runner(standardUserHome), "list");
      CliLaunchResult extensionResult = launchCapturingOutput(runner(extensionUserHome), "list");

      List<String> standardSlugs = moduleSlugs(standardResult.output());
      List<String> extensionSlugs = moduleSlugs(extensionResult.output());
      Set<String> addedSlugs = setDifference(Set.copyOf(extensionSlugs), Set.copyOf(standardSlugs));
      Set<String> removedSlugs = setDifference(Set.copyOf(standardSlugs), Set.copyOf(extensionSlugs));
      assertThat(standardResult.exitCode()).isZero();
      assertThat(extensionResult.exitCode()).isZero();
      assertThat(standardSlugs).doesNotContain(EXTENSION_ONLY_SLUG).doesNotHaveDuplicates();
      assertThat(extensionSlugs).contains(EXTENSION_ONLY_SLUG).doesNotHaveDuplicates();
      assertThat(addedSlugs).containsExactly(EXTENSION_ONLY_SLUG);
      assertThat(removedSlugs).isEmpty();
      assertBaselineRuntimePropertiesRestored();
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldListCustomPackageExtensionOnlySlug() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-custom-extension-catalog-primary-");
    ExtensionRuntimeFixture.installWithCustomPackageListExtensionModule(userHome);
    ScopedSystemProperties baselineProperties = capturedRuntimeProperties();

    try {
      setBaselineRuntimeProperties();

      CliLaunchResult listLaunch = launchCapturingOutput(runner(userHome), "list");

      assertThat(listLaunch.exitCode()).isZero();
      assertThat(moduleSlugs(listLaunch.output())).contains(CUSTOM_PACKAGE_EXTENSION_ONLY_SLUG).doesNotHaveDuplicates();
      assertBaselineRuntimePropertiesRestored();
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldKeepCoreModulesVisibleWhenExtensionPublishesHiddenResourceOverrides() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-hidden-resources-primary-");
    ExtensionRuntimeFixture.installWithListExtensionModuleAndHiddenResourcesOverrides(userHome);
    ScopedSystemProperties baselineProperties = capturedRuntimeProperties();

    try {
      setBaselineRuntimeProperties();

      CliLaunchResult listLaunch = launchCapturingOutput(runner(userHome), "list");

      assertThat(listLaunch.exitCode()).isZero();
      assertThat(listLaunch.output()).contains(EXTENSION_ONLY_SLUG).contains(CORE_SLUG_THAT_EXTENSION_TRIES_TO_HIDE);
      assertBaselineRuntimePropertiesRestored();
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldKeepOperationalOutputCleanWhenExtensionPublishesLoggingOverrides() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-logging-primary-");
    ExtensionRuntimeFixture.installWithListExtensionModuleAndRegressionOverrides(userHome);
    PreSpringBootstrapRunner runner = runner(userHome);
    ScopedSystemProperties baselineProperties = capturedRuntimeProperties();

    try {
      setBaselineRuntimeProperties();

      CliLaunchResult versionLaunch = launchCapturingOutput(runner, "--version");
      CliLaunchResult listLaunch = launchCapturingOutput(runner, "list");

      assertThat(versionLaunch.exitCode()).isZero();
      assertThat(versionLaunch.output())
        .contains("Runtime mode: extension")
        .contains("Distribution ID: company-extension")
        .contains("Distribution version: 1.0.0")
        .doesNotContain(SPRING_BOOT_BANNER_MARKER)
        .doesNotContain(STARTUP_INFO_MARKER)
        .doesNotContain(EXTENSION_LOGBACK_OVERRIDE_MARKER)
        .doesNotContain(EXTENSION_APPLICATION_OVERRIDE_MARKER)
        .doesNotContain("Missing watchable .xml or .properties files")
        .doesNotContain("Watching .xml files requires that the main configuration file is reachable as a URL");
      assertThat(listLaunch.exitCode()).isZero();
      assertThat(listLaunch.output())
        .contains(EXTENSION_ONLY_SLUG)
        .doesNotContain(SPRING_BOOT_BANNER_MARKER)
        .doesNotContain(STARTUP_INFO_MARKER)
        .doesNotContain(EXTENSION_LOGBACK_OVERRIDE_MARKER)
        .doesNotContain(EXTENSION_APPLICATION_OVERRIDE_MARKER)
        .doesNotContain("Missing watchable .xml or .properties files")
        .doesNotContain("Watching .xml files requires that the main configuration file is reachable as a URL");
      assertBaselineRuntimePropertiesRestored();
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldApplyExtensionModuleUsingSharedRuntimeResources() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-apply-shared-runtime-primary-");
    Path projectPath = Files.createTempDirectory("seed4j-cli-apply-shared-runtime-project-primary-");
    ExtensionRuntimeFixture.installWithApplyExtensionModuleUsingSharedRuntimeOverrides(userHome);
    PreSpringBootstrapRunner runner = runner(userHome);
    ScopedSystemProperties baselineProperties = capturedRuntimeProperties();

    try {
      setBaselineRuntimeProperties();

      CliLaunchResult initResult = applyInit(runner, projectPath);
      CliLaunchResult extensionModuleApplyResult = launchCapturingOutput(
        runner,
        "apply",
        EXTENSION_SHARED_RUNTIME_APPLY_MODULE_SLUG,
        "--project-path",
        projectPath.toString(),
        "--no-commit"
      );

      assertThat(initResult.exitCode()).isZero();
      assertThat(extensionModuleApplyResult.exitCode())
        .withFailMessage("Expected extension apply module command to succeed but got output:%n%s", extensionModuleApplyResult.output())
        .isZero();
      assertThat(Files.readString(projectPath.resolve("package.json"))).contains("\"prettier\": \"" + OVERRIDDEN_PRETTIER_VERSION + "\"");
      assertThat(Files.readString(projectPath.resolve(".prettierrc"))).contains(OVERRIDDEN_PRETTIER_TEMPLATE_MARKER);
      assertBaselineRuntimePropertiesRestored();
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldOverrideCorePrettierDependencyVersionsOnlyInExtensionMode() throws IOException {
    Path standardUserHome = Files.createTempDirectory("seed4j-cli-apply-common-standard-primary-");
    Path extensionUserHome = Files.createTempDirectory("seed4j-cli-apply-common-extension-primary-");
    Path standardProjectPath = Files.createTempDirectory("seed4j-cli-apply-common-standard-project-primary-");
    Path extensionProjectPath = Files.createTempDirectory("seed4j-cli-apply-common-extension-project-primary-");
    ExtensionRuntimeFixture.installWithApplyCommonSourceOverrideExtensionModule(extensionUserHome);
    ScopedSystemProperties baselineProperties = capturedRuntimeProperties();

    try {
      setBaselineRuntimeProperties();

      CliLaunchResult standardInitResult = applyInit(runner(standardUserHome), standardProjectPath);
      CliLaunchResult extensionInitResult = applyInit(runner(extensionUserHome), extensionProjectPath);
      CliLaunchResult standardPrettierResult = applyPrettier(runner(standardUserHome), standardProjectPath);
      CliLaunchResult extensionPrettierResult = applyPrettier(runner(extensionUserHome), extensionProjectPath);

      assertThat(standardInitResult.exitCode()).isZero();
      assertThat(extensionInitResult.exitCode())
        .withFailMessage("Expected extension init command to succeed but got output:%n%s", extensionInitResult.output())
        .isZero();
      assertThat(standardPrettierResult.exitCode()).isZero();
      assertThat(extensionPrettierResult.exitCode())
        .withFailMessage("Expected extension prettier command to succeed but got output:%n%s", extensionPrettierResult.output())
        .isZero();
      assertThat(Files.readString(standardProjectPath.resolve("package.json"))).doesNotContain(
        "\"prettier\": \"" + OVERRIDDEN_PRETTIER_VERSION + "\""
      );
      assertThat(Files.readString(extensionProjectPath.resolve("package.json"))).contains(
        "\"prettier\": \"" + OVERRIDDEN_PRETTIER_VERSION + "\""
      );
      assertBaselineRuntimePropertiesRestored();
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldFailBeforeChildRuntimeWhenExtensionRuntimeJarIsFlat() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-invalid-extension-primary-");
    ExtensionRuntimeFixture.installWithFlatJar(userHome);
    ScopedSystemProperties baselineProperties = capturedRuntimeProperties();

    try {
      setBaselineRuntimeProperties();

      CliLaunchResult versionLaunch = launchCapturingOutput(runner(userHome), "--version");

      assertThat(versionLaunch.exitCode()).isNotZero();
      assertThat(versionLaunch.output()).contains("BOOT-INF/classes");
      assertBaselineRuntimePropertiesRestored();
    } finally {
      baselineProperties.restore();
    }
  }

  @Test
  void shouldLaunchTheStandardChildRuntimeWithArguments() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-standard-child-primary-");
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

    CliLaunchResult launch = launchCapturingOutput(fixture.runner(), "--version");

    assertThat(launch.exitCode()).isZero();
    assertThat(fixture.childLaunchRequest()).isNotNull();
    assertThat(fixture.childLaunchRequest().executableJar()).isEqualTo(fixture.executablePath());
    assertThat(fixture.childLaunchRequest().runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(fixture.childLaunchRequest().runtimeSelection().distributionId()).isEmpty();
    assertThat(fixture.childLaunchRequest().runtimeSelection().distributionVersion()).isEmpty();
    assertThat(fixture.childLaunchRequest().runtimeSelection().extensionJarPath()).isEmpty();
    assertThat(fixture.childLaunchRequest().arguments().asList()).containsExactly("--version");
    assertThat(fixture.childLaunchRequest().debug()).isFalse();
    assertThat(fixture.localRunArguments()).isEmpty();
  }

  @Test
  void shouldIgnoreLegacyRuntimeConfigPath() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-legacy-config-primary-");
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
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

    CliLaunchResult launch = launchCapturingOutput(fixture.runner(), "--version");

    assertThat(launch.exitCode()).isZero();
    assertThat(fixture.childLaunchRequest()).isNotNull();
    assertThat(fixture.childLaunchRequest().runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(fixture.localRunArguments()).isEmpty();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("standardModeConfigurationContents")
  void shouldLaunchTheStandardChildRuntimeWhenConfigDoesNotSelectExtensionMode(String scenarioName, String configContent)
    throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-standard-config-primary-");
    writeRuntimeConfiguration(userHome, configContent);
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

    CliLaunchResult launch = launchCapturingOutput(fixture.runner(), "--version");

    assertThat(launch.exitCode()).isZero();
    assertThat(fixture.childLaunchRequest()).isNotNull();
    assertThat(fixture.childLaunchRequest().runtimeSelection().mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(fixture.childLaunchRequest().runtimeSelection().distributionId()).isEmpty();
    assertThat(fixture.childLaunchRequest().runtimeSelection().distributionVersion()).isEmpty();
    assertThat(fixture.childLaunchRequest().debug()).isFalse();
    assertThat(fixture.localRunArguments()).isEmpty();
  }

  @Test
  void shouldLaunchTheExtensionChildRuntimeWithActiveDistribution() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-child-primary-");
    ExtensionRuntimeFixturePaths extensionPaths = ExtensionRuntimeFixture.install(userHome);
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

    CliLaunchResult launch = launchCapturingOutput(fixture.runner(), "--version");

    assertThat(launch.exitCode()).isZero();
    assertThat(fixture.childLaunchRequest()).isNotNull();
    assertThat(fixture.childLaunchRequest().runtimeSelection().mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(fixture.childLaunchRequest().runtimeSelection().distributionId()).contains(new RuntimeDistributionId("company-extension"));
    assertThat(fixture.childLaunchRequest().runtimeSelection().distributionVersion()).contains(new RuntimeDistributionVersion("1.0.0"));
    assertThat(fixture.childLaunchRequest().runtimeSelection().extensionJarPath()).contains(
      new RuntimeExtensionJarPath(extensionPaths.extensionJarPath())
    );
    assertThat(fixture.childLaunchRequest().arguments().asList()).containsExactly("--version");
    assertThat(fixture.childLaunchRequest().debug()).isFalse();
    assertThat(fixture.localRunArguments()).isEmpty();
  }

  @Test
  void shouldPropagateDebugToExtensionChildRuntimeAndParentDiagnostics() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-debug-primary-");
    ExtensionRuntimeFixture.install(userHome);
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

    CliLaunchResult launch = launchCapturingOutput(fixture.runner(), "--version", "--debug");

    assertThat(launch.exitCode()).isZero();
    assertThat(fixture.childLaunchRequest()).isNotNull();
    assertThat(fixture.childLaunchRequest().runtimeSelection().mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(fixture.childLaunchRequest().debug()).isTrue();
    assertThat(fixture.debugLoggingEnabled()).isTrue();
    assertThat(fixture.localRunArguments()).isEmpty();
  }

  @Test
  void shouldRunStandardModeLocallyOutsidePackagedJar() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-standard-local-primary-");
    Path executableDirectory = Files.createTempDirectory("seed4j-cli-classes-primary-");
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.withExecutablePath(userHome, executableDirectory, false);

    CliLaunchResult launch = launchCapturingOutput(fixture.runner(), "--version");

    assertThat(launch.exitCode()).isEqualTo(12);
    assertThat(launch.output()).contains("not running from a packaged CLI JAR");
    assertThat(fixture.localRunArguments()).containsExactly("--version");
    assertThat(fixture.childLaunchRequest()).isNull();
  }

  @Test
  void shouldRunLocallyWhenAlreadyExecutingAsAChildRuntime() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-child-mode-primary-");
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.withExecutablePath(
      userHome,
      Files.createTempFile("seed4j-cli-", ".jar"),
      true
    );

    CliLaunchResult launch = launchCapturingOutput(fixture.runner(), "--version");

    assertThat(launch.exitCode()).isEqualTo(12);
    assertThat(fixture.localRunArguments()).containsExactly("--version");
    assertThat(fixture.childLaunchRequest()).isNull();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidRuntimeConfigurationContents")
  void shouldFailBeforeChildRuntimeWhenRuntimeConfigurationIsInvalid(String scenarioName, String configContent) throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-invalid-config-primary-");
    writeRuntimeConfiguration(userHome, configContent);
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

    CliLaunchResult launch = launchCapturingOutput(fixture.runner(), "--version");

    assertThat(launch.exitCode()).isNotZero();
    assertThat(launch.output()).isNotBlank();
    assertThat(fixture.childLaunchRequest()).isNull();
    assertThat(fixture.localRunArguments()).isEmpty();
  }

  @Test
  void shouldFailBeforeChildRuntimeWhenRuntimeConfigurationCannotBeRead() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-unreadable-config-primary-");
    Files.createDirectories(runtimeConfigurationPath(userHome));
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

    CliLaunchResult launch = launchCapturingOutput(fixture.runner(), "--version");

    assertThat(launch.exitCode()).isNotZero();
    assertThat(launch.output()).isNotBlank();
    assertThat(fixture.childLaunchRequest()).isNull();
    assertThat(fixture.localRunArguments()).isEmpty();
  }

  @Test
  void shouldFailBeforeChildRuntimeWhenExtensionModeRunsOutsidePackagedJar() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-local-primary-");
    ExtensionRuntimeFixture.install(userHome);
    Path executableDirectory = Files.createTempDirectory("seed4j-cli-extension-classes-primary-");
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.withExecutablePath(userHome, executableDirectory, false);

    CliLaunchResult launch = launchCapturingOutput(fixture.runner(), "--version");

    assertThat(launch.exitCode()).isNotZero();
    assertThat(launch.output()).contains("Extension mode requires running the packaged CLI JAR");
    assertThat(fixture.childLaunchRequest()).isNull();
    assertThat(fixture.localRunArguments()).isEmpty();
  }

  private static CliLaunchResult applyInit(PreSpringBootstrapRunner runner, Path projectPath) {
    return apply(runner, projectPath, "init", "--node-package-manager", "npm");
  }

  private static CliLaunchResult applyPrettier(PreSpringBootstrapRunner runner, Path projectPath) {
    return apply(runner, projectPath, "prettier");
  }

  private static CliLaunchResult apply(
    PreSpringBootstrapRunner runner,
    Path projectPath,
    String moduleSlug,
    String... additionalArguments
  ) {
    List<String> arguments = new ArrayList<>(
      List.of(
        "apply",
        moduleSlug,
        "--project-path",
        projectPath.toString(),
        "--base-name",
        "sampleapp",
        "--project-name",
        "Sample App",
        "--no-commit"
      )
    );
    arguments.addAll(List.of(additionalArguments));
    return launchCapturingOutput(runner, arguments.toArray(String[]::new));
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

  private static PreSpringBootstrapRunner runner(Path userHome) throws IOException {
    Seed4JCliHome cliHome = new Seed4JCliHome(userHome);
    Path executableJar = Files.createTempFile("seed4j-cli-", ".jar");
    LocalCliRunner localCliRunner = new SpringBootLocalCliRunner(TestSeed4JCliApp.class, cliHome);
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(cliHome, executableJar, false, javaExecutablePath());
    PreSpringBootstrapApplicationService preSpringBootstrapApplicationService = new PreSpringBootstrapApplicationService(
      new PreSpringRuntimeEnvironmentSeed4JCliRuntime(runtimeEnvironment),
      new FileSystemRuntimeModeConfigurationRepository(cliHome),
      new FileSystemRuntimeExtensionSelectionRepository(cliHome, new JarRuntimeExtensionPackageValidator()),
      new InProcessChildRuntimeLauncher(cliHome, localCliRunner),
      localCliRunner,
      new FileSystemPackagedExecutableDetector(),
      () -> {},
      new SystemErrBootstrapOutput()
    );
    return new PreSpringBootstrapRunner(preSpringBootstrapApplicationService);
  }

  private static void writeRuntimeConfiguration(Path userHome, String content) throws IOException {
    Path configPath = runtimeConfigurationPath(userHome);
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, content);
  }

  private static Path runtimeConfigurationPath(Path userHome) {
    return userHome.resolve(".config/seed4j-cli/config.yml");
  }

  private static Path javaExecutablePath() {
    return Path.of(System.getProperty("java.home"), "bin", "java");
  }

  private static CliLaunchResult launchCapturingOutput(PreSpringBootstrapRunner runner, String... arguments) {
    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = runner.exitCodeFor(arguments);
      return new CliLaunchResult(exitCode, outputCaptor.getOutput());
    }
  }

  private static ScopedSystemProperties capturedRuntimeProperties() {
    return ScopedSystemProperties.capture(
      Set.of(RUNTIME_MODE_PROPERTY, DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY)
    );
  }

  private static void setBaselineRuntimeProperties() {
    System.setProperty(RUNTIME_MODE_PROPERTY, BASELINE_RUNTIME_MODE);
    System.clearProperty(DISTRIBUTION_ID_PROPERTY);
    System.clearProperty(DISTRIBUTION_VERSION_PROPERTY);
    System.clearProperty(LOADER_PATH_PROPERTY);
  }

  private static void assertBaselineRuntimePropertiesRestored() {
    assertThat(System.getProperty(RUNTIME_MODE_PROPERTY)).isEqualTo(BASELINE_RUNTIME_MODE);
    assertThat(System.getProperty(DISTRIBUTION_ID_PROPERTY)).isNull();
    assertThat(System.getProperty(DISTRIBUTION_VERSION_PROPERTY)).isNull();
    assertThat(System.getProperty(LOADER_PATH_PROPERTY)).isNull();
  }

  private static List<String> moduleSlugs(String output) {
    return output.lines().map(PreSpringBootstrapPrimaryTest::moduleSlugFromLine).flatMap(Optional::stream).toList();
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
    Set<String> remainingSlugs = new LinkedHashSet<>();
    for (String sourceSlug : sourceSlugs) {
      if (!slugsToExclude.contains(sourceSlug)) {
        remainingSlugs.add(sourceSlug);
      }
    }
    return remainingSlugs;
  }

  private static final class PrimaryBootstrapFixture {

    private final Path executablePath;
    private final RecordingChildRuntimeLauncher childRuntimeLauncher;
    private final RecordingLocalCliRunner localCliRunner;
    private final RecordingBootstrapDiagnostics bootstrapDiagnostics;
    private final PreSpringBootstrapRunner runner;

    private PrimaryBootstrapFixture(
      Path executablePath,
      RecordingChildRuntimeLauncher childRuntimeLauncher,
      RecordingLocalCliRunner localCliRunner,
      RecordingBootstrapDiagnostics bootstrapDiagnostics,
      PreSpringBootstrapRunner runner
    ) {
      this.executablePath = executablePath;
      this.childRuntimeLauncher = childRuntimeLauncher;
      this.localCliRunner = localCliRunner;
      this.bootstrapDiagnostics = bootstrapDiagnostics;
      this.runner = runner;
    }

    private static PrimaryBootstrapFixture packaged(Path userHome) throws IOException {
      return withExecutablePath(userHome, Files.createTempFile("seed4j-cli-", ".jar"), false);
    }

    private static PrimaryBootstrapFixture withExecutablePath(Path userHome, Path executablePath, boolean childMode) {
      Seed4JCliHome cliHome = new Seed4JCliHome(userHome);
      RecordingChildRuntimeLauncher childRuntimeLauncher = new RecordingChildRuntimeLauncher();
      RecordingLocalCliRunner localCliRunner = new RecordingLocalCliRunner();
      RecordingBootstrapDiagnostics bootstrapDiagnostics = new RecordingBootstrapDiagnostics();
      PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
        cliHome,
        executablePath,
        childMode,
        javaExecutablePath()
      );
      PreSpringBootstrapApplicationService preSpringBootstrapApplicationService = new PreSpringBootstrapApplicationService(
        new PreSpringRuntimeEnvironmentSeed4JCliRuntime(runtimeEnvironment),
        new FileSystemRuntimeModeConfigurationRepository(cliHome),
        new FileSystemRuntimeExtensionSelectionRepository(cliHome, new JarRuntimeExtensionPackageValidator()),
        childRuntimeLauncher,
        localCliRunner,
        new FileSystemPackagedExecutableDetector(),
        bootstrapDiagnostics,
        new SystemErrBootstrapOutput()
      );
      return new PrimaryBootstrapFixture(
        executablePath,
        childRuntimeLauncher,
        localCliRunner,
        bootstrapDiagnostics,
        new PreSpringBootstrapRunner(preSpringBootstrapApplicationService)
      );
    }

    private PreSpringBootstrapRunner runner() {
      return runner;
    }

    private Path executablePath() {
      return executablePath;
    }

    private ChildRuntimeLaunchRequest childLaunchRequest() {
      return childRuntimeLauncher.request();
    }

    private List<String> localRunArguments() {
      return localCliRunner.arguments();
    }

    private boolean debugLoggingEnabled() {
      return bootstrapDiagnostics.enabled();
    }
  }

  private static final class RecordingChildRuntimeLauncher implements ChildRuntimeLauncher {

    private ChildRuntimeLaunchRequest request;

    @Override
    public int launch(ChildRuntimeLaunchRequest request) {
      this.request = request;
      return 0;
    }

    private ChildRuntimeLaunchRequest request() {
      return request;
    }
  }

  private static final class RecordingLocalCliRunner implements LocalCliRunner {

    private List<String> arguments = List.of();

    @Override
    public int run(Seed4JCliArguments arguments) {
      this.arguments = arguments.asList();
      return 12;
    }

    private List<String> arguments() {
      return arguments;
    }
  }

  private static final class RecordingBootstrapDiagnostics implements BootstrapDiagnostics {

    private boolean enabled;

    @Override
    public void enableDebugLogging() {
      enabled = true;
    }

    private boolean enabled() {
      return enabled;
    }
  }

  private static final class InProcessChildRuntimeLauncher implements ChildRuntimeLauncher {

    private final Seed4JCliHome cliHome;
    private final LocalCliRunner localCliRunner;

    private InProcessChildRuntimeLauncher(Seed4JCliHome cliHome, LocalCliRunner localCliRunner) {
      this.cliHome = cliHome;
      this.localCliRunner = localCliRunner;
    }

    @Override
    public int launch(ChildRuntimeLaunchRequest request) {
      Map<String, String> systemProperties = systemProperties(request);
      ScopedSystemProperties scopedSystemProperties = ScopedSystemProperties.capture(systemProperties.keySet());
      Thread currentThread = Thread.currentThread();
      ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
      URLClassLoader childRuntimeClassLoader = childRuntimeClassLoader(request, originalContextClassLoader);
      try {
        currentThread.setContextClassLoader(childRuntimeClassLoader);
        systemProperties.forEach(System::setProperty);
        return localCliRunner.run(request.arguments());
      } finally {
        currentThread.setContextClassLoader(originalContextClassLoader);
        closeQuietly(childRuntimeClassLoader);
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
            new RuntimeExtensionStartClassResolver().resolve(rawExtensionJarPath)
          );
          Path overlayClassesPath = new RuntimeExtensionOverlayCache(cliHome).materialize(rawExtensionJarPath);
          systemProperties.put(
            LOADER_PATH_PROPERTY,
            new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, rawExtensionJarPath, request.executableJar())
          );
        });
      systemProperties.put("logging.config", "classpath:seed4j-cli-logback-spring.xml");
      systemProperties.put("logging.level.root", "ERROR");
      systemProperties.put("spring.main.log-startup-info", "false");
      return Map.copyOf(systemProperties);
    }

    private URLClassLoader childRuntimeClassLoader(ChildRuntimeLaunchRequest request, ClassLoader parentClassLoader) {
      return request
        .runtimeSelection()
        .extensionJarPath()
        .map(extensionJarPath -> childRuntimeClassLoader(request, extensionJarPath.path(), parentClassLoader))
        .orElseGet(() -> new URLClassLoader(new URL[0], parentClassLoader));
    }

    private URLClassLoader childRuntimeClassLoader(
      ChildRuntimeLaunchRequest request,
      Path rawExtensionJarPath,
      ClassLoader parentClassLoader
    ) {
      try {
        Path overlayClassesPath = new RuntimeExtensionOverlayCache(cliHome).materialize(rawExtensionJarPath);
        return new ChildFirstRuntimeExtensionResourceClassLoader(
          childRuntimeAndTestClasspathUrls(overlayClassesPath, rawExtensionJarPath, request.executableJar()),
          parentClassLoader
        );
      } catch (MalformedURLException malformedURLException) {
        throw new IllegalStateException(
          "Could not create child runtime classloader for extension jar: " + rawExtensionJarPath,
          malformedURLException
        );
      }
    }

    private URL[] childRuntimeAndTestClasspathUrls(Path overlayClassesPath, Path rawExtensionJarPath, Path executableJarPath)
      throws MalformedURLException {
      List<URL> urls = new ArrayList<>();
      urls.add(overlayClassesPath.toUri().toURL());
      urls.add(rawExtensionJarPath.toUri().toURL());
      urls.add(executableJarPath.toUri().toURL());
      for (String classPathEntry : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))) {
        if (!classPathEntry.isBlank()) {
          urls.add(Path.of(classPathEntry).toUri().toURL());
        }
      }
      return urls.toArray(URL[]::new);
    }

    private void closeQuietly(URLClassLoader childRuntimeClassLoader) {
      try {
        childRuntimeClassLoader.close();
      } catch (IOException _) {
        // Closing the test classloader must not mask the launch result.
      }
    }
  }

  private static final class ChildFirstRuntimeExtensionResourceClassLoader extends URLClassLoader {

    private static final String FIXTURE_CLASS_PREFIX = "com.mycompany.seed4j.extension.runtime.";
    private static final String CLASS_ANCHORED_PROJECT_FILES_READER = "com.seed4j.module.infrastructure.secondary.FileSystemProjectFiles";
    private static final String FIXTURE_RESOURCE_PREFIX = "com/mycompany/seed4j/extension/runtime/";
    private static final String GENERATOR_RESOURCE_PREFIX = "generator/";

    private ChildFirstRuntimeExtensionResourceClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass == null) {
          loadedClass = loadClassFromExtensionOrParent(name);
        }
        if (resolve) {
          resolveClass(loadedClass);
        }
        return loadedClass;
      }
    }

    private Class<?> loadClassFromExtensionOrParent(String className) throws ClassNotFoundException {
      if (childFirstClass(className)) {
        return findClass(className);
      }

      return super.loadClass(className, false);
    }

    private boolean childFirstClass(String className) {
      return extensionFixtureClass(className) || classAnchoredResourceReader(className);
    }

    private boolean extensionFixtureClass(String className) {
      return className.startsWith(FIXTURE_CLASS_PREFIX);
    }

    private boolean classAnchoredResourceReader(String className) {
      return CLASS_ANCHORED_PROJECT_FILES_READER.equals(className);
    }

    @Override
    public URL getResource(String name) {
      if (extensionResourceLookup(name)) {
        URL extensionResource = findResource(name);
        if (extensionResource != null) {
          return extensionResource;
        }
      }

      return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
      if (!extensionResourceLookup(name)) {
        return super.getResources(name);
      }

      List<URL> resources = new ArrayList<>();
      Enumeration<URL> extensionResources = findResources(name);
      while (extensionResources.hasMoreElements()) {
        resources.add(extensionResources.nextElement());
      }
      Enumeration<URL> parentResources = super.getResources(name);
      while (parentResources.hasMoreElements()) {
        resources.add(parentResources.nextElement());
      }
      return Collections.enumeration(resources);
    }

    private boolean extensionResourceLookup(String name) {
      return name.startsWith(FIXTURE_RESOURCE_PREFIX) || name.startsWith(GENERATOR_RESOURCE_PREFIX);
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @ComponentScan(basePackages = "com.seed4j.cli")
  public static class TestSeed4JCliApp {}

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
      for (Map.Entry<String, Optional<String>> originalValue : originalValues.entrySet()) {
        String propertyKey = originalValue.getKey();
        Optional<String> propertyValue = originalValue.getValue();
        if (propertyValue.isPresent()) {
          System.setProperty(propertyKey, propertyValue.orElseThrow());
        } else {
          System.clearProperty(propertyKey);
        }
      }
    }
  }
}
