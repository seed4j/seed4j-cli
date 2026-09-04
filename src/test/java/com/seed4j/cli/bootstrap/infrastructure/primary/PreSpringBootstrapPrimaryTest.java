package com.seed4j.cli.bootstrap.infrastructure.primary;

import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.DISTRIBUTION_ID_PROPERTY;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.DISTRIBUTION_VERSION_PROPERTY;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.LOADER_PATH_PROPERTY;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.RUNTIME_MODE_PROPERTY;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.baselineRuntimeProperties;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.launchCapturingOutput;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.runtimeConfigurationPath;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.writeConfiguration;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.writeRuntimeConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.BootstrapDebugMode;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliExecutablePath;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture.ExtensionRuntimeFixturePaths;
import com.seed4j.cli.bootstrap.infrastructure.primary.ExtensionCatalogFixture.VersionAndListLaunch;
import com.seed4j.cli.bootstrap.infrastructure.primary.ModuleCatalogOutput.Comparison;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.CliLaunchResult;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.ScopedRuntimeProperties;
import com.seed4j.cli.bootstrap.infrastructure.primary.PrimaryBootstrapFixture.BootstrapLaunch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.SAME_THREAD)
@UnitTest
class PreSpringBootstrapPrimaryTest {

  @Nested
  class RuntimeDispatch {

    @Test
    void shouldLaunchTheStandardChildRuntimeWithArguments() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-standard-child-primary-");
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);
      ChildRuntimeLaunchRequest expectedRequest = new ChildRuntimeLaunchRequest(
        new Seed4JCliExecutablePath(fixture.executablePath()),
        com.seed4j.cli.bootstrap.domain.RuntimeSelection.standard(),
        new Seed4JCliArguments(new String[] { "--version" }),
        BootstrapDebugMode.DISABLED
      );

      BootstrapLaunch launch = fixture.launch("--version");

      assertThat(launch.result().exitCode()).isZero();
      assertThat(launch.childRuntimeRequest()).isEqualTo(expectedRequest);
      assertThat(fixture.localRunArguments()).isEmpty();
    }

    @Test
    void shouldPropagateChildRuntimeExitCode() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-child-exit-code-primary-");
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);
      int expectedExitCode = 37;
      fixture.childRuntimeReturns(expectedExitCode);

      BootstrapLaunch launch = fixture.launch("--version");

      assertThat(launch.result().exitCode()).isEqualTo(expectedExitCode);
    }

    @Test
    void shouldLaunchTheExtensionChildRuntimeWithActiveDistribution() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-extension-child-primary-");
      ExtensionRuntimeFixturePaths extensionPaths = ExtensionRuntimeFixture.install(userHome);
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);
      ChildRuntimeLaunchRequest expectedRequest = new ChildRuntimeLaunchRequest(
        new Seed4JCliExecutablePath(fixture.executablePath()),
        com.seed4j.cli.bootstrap.domain.RuntimeSelection.extension(
          new RuntimeExtensionJarPath(extensionPaths.extensionJarPath()),
          new RuntimeDistributionId("company-extension"),
          new RuntimeDistributionVersion("1.0.0")
        ),
        new Seed4JCliArguments(new String[] { "--version" }),
        BootstrapDebugMode.DISABLED
      );

      BootstrapLaunch launch = fixture.launch("--version");

      assertThat(launch.result().exitCode()).isZero();
      assertThat(launch.childRuntimeRequest()).isEqualTo(expectedRequest);
      assertThat(fixture.localRunArguments()).isEmpty();
    }

    @Test
    void shouldPropagateDebugToExtensionChildRuntimeAndParentDiagnostics() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-extension-debug-primary-");
      ExtensionRuntimeFixturePaths extensionPaths = ExtensionRuntimeFixture.install(userHome);
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);
      ChildRuntimeLaunchRequest expectedRequest = new ChildRuntimeLaunchRequest(
        new Seed4JCliExecutablePath(fixture.executablePath()),
        com.seed4j.cli.bootstrap.domain.RuntimeSelection.extension(
          new RuntimeExtensionJarPath(extensionPaths.extensionJarPath()),
          new RuntimeDistributionId("company-extension"),
          new RuntimeDistributionVersion("1.0.0")
        ),
        new Seed4JCliArguments(new String[] { "--version", "--debug" }),
        BootstrapDebugMode.ENABLED
      );

      BootstrapLaunch launch = fixture.launch("--version", "--debug");

      assertThat(launch.result().exitCode()).isZero();
      assertThat(launch.childRuntimeRequest()).isEqualTo(expectedRequest);
      assertThat(fixture.debugLoggingEnabled()).isTrue();
      assertThat(fixture.localRunArguments()).isEmpty();
    }

    @Test
    void shouldRunStandardModeLocallyOutsidePackagedJar() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-standard-local-primary-");
      Path executableDirectory = Files.createTempDirectory("seed4j-cli-classes-primary-");
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.unpackaged(userHome, executableDirectory);

      BootstrapLaunch launch = fixture.launch("--version");

      assertThat(launch.result().exitCode()).isEqualTo(12);
      assertThat(launch.result().output()).contains("not running from a packaged CLI JAR");
      assertThat(fixture.localRunArguments()).containsExactly("--version");
      assertThat(fixture.childLaunchRequest()).isNull();
    }

    @Test
    void shouldRunLocallyWhenAlreadyExecutingAsAChildRuntime() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-child-mode-primary-");
      Path executableJar = Files.createTempFile("seed4j-cli-", ".jar");
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.child(userHome, executableJar);

      BootstrapLaunch launch = fixture.launch("--version");

      assertThat(launch.result().exitCode()).isEqualTo(12);
      assertThat(fixture.localRunArguments()).containsExactly("--version");
      assertThat(fixture.childLaunchRequest()).isNull();
    }
  }

  @Nested
  class RuntimeSelection {

    @Test
    void shouldIgnoreLegacyRuntimeConfigPath() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-legacy-config-primary-");
      writeConfiguration(
        userHome.resolve(".config/seed4j-cli.yml"),
        """
        seed4j:
          runtime:
            mode: extension
        """
      );
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

      BootstrapLaunch launch = fixture.launch("--version");
      ChildRuntimeLaunchRequest childLaunchRequest = launch.childRuntimeRequest();

      assertThat(launch.result().exitCode()).isZero();
      assertThat(childLaunchRequest).isNotNull();
      assertThat(childLaunchRequest.runtimeSelection()).isEqualTo(com.seed4j.cli.bootstrap.domain.RuntimeSelection.standard());
      assertThat(fixture.localRunArguments()).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("standardMode")
    void shouldLaunchTheStandardChildRuntimeWhenConfigDoesNotSelectExtensionMode(String scenarioName, String configContent)
      throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-standard-config-primary-");
      writeRuntimeConfiguration(userHome, configContent);
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

      BootstrapLaunch launch = fixture.launch("--version");
      ChildRuntimeLaunchRequest childLaunchRequest = launch.childRuntimeRequest();

      assertThat(launch.result().exitCode()).isZero();
      assertThat(childLaunchRequest).isNotNull();
      assertThat(childLaunchRequest.runtimeSelection()).isEqualTo(com.seed4j.cli.bootstrap.domain.RuntimeSelection.standard());
      assertThat(childLaunchRequest.debug().enabled()).isFalse();
      assertThat(fixture.localRunArguments()).isEmpty();
    }

    static Stream<Arguments> standardMode() {
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

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalid")
    void shouldFailBeforeChildRuntimeWhenRuntimeConfigurationIsInvalid(String scenarioName, String configContent) throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-invalid-config-primary-");
      writeRuntimeConfiguration(userHome, configContent);
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

      BootstrapLaunch launch = fixture.launch("--version");

      assertThat(launch.result().exitCode()).isNotZero();
      assertThat(launch.result().output()).isNotBlank();
      assertThat(fixture.childLaunchRequest()).isNull();
      assertThat(fixture.localRunArguments()).isEmpty();
    }

    static Stream<Arguments> invalid() {
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

    @Test
    void shouldFailBeforeChildRuntimeWhenRuntimeConfigurationCannotBeRead() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-unreadable-config-primary-");
      Files.createDirectories(runtimeConfigurationPath(userHome));
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

      BootstrapLaunch launch = fixture.launch("--version");

      assertThat(launch.result().exitCode()).isNotZero();
      assertThat(launch.result().output()).isNotBlank();
      assertThat(fixture.childLaunchRequest()).isNull();
      assertThat(fixture.localRunArguments()).isEmpty();
    }

    @Test
    void shouldFailBeforeChildRuntimeWhenExtensionRuntimeJarIsFlat() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-invalid-extension-primary-");
      ExtensionRuntimeFixture.installWithFlatJar(userHome);

      try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
        CliLaunchResult versionLaunch = launchCapturingOutput(InProcessChildRuntimeLauncher.runner(userHome), "--version");

        assertThat(versionLaunch.exitCode()).isNotZero();
        assertThat(versionLaunch.output()).contains("BOOT-INF/classes");
        assertThat(System.getProperties())
          .containsEntry(RUNTIME_MODE_PROPERTY, "baseline-mode")
          .doesNotContainKeys(DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY);
      }
    }

    @Test
    void shouldFailBeforeChildRuntimeWhenExtensionModeRunsOutsidePackagedJar() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-extension-local-primary-");
      ExtensionRuntimeFixture.install(userHome);
      Path executableDirectory = Files.createTempDirectory("seed4j-cli-extension-classes-primary-");
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.unpackaged(userHome, executableDirectory);

      BootstrapLaunch launch = fixture.launch("--version");

      assertThat(launch.result().exitCode()).isNotZero();
      assertThat(launch.result().output()).contains("Extension mode requires running the packaged CLI JAR");
      assertThat(fixture.childLaunchRequest()).isNull();
      assertThat(fixture.localRunArguments()).isEmpty();
    }
  }

  @Nested
  class ExtensionCatalog {

    private static final String EXTENSION_ONLY_SLUG = "runtime-extension-list-only";
    private static final String CUSTOM_PACKAGE_EXTENSION_ONLY_SLUG = "runtime-extension-custom-package-list-only";
    private static final String CORE_SLUG_THAT_EXTENSION_TRIES_TO_HIDE = "gradle-java";
    private static final String[] EXPECTED_EXTENSION_VERSION_OUTPUT = {
      "Runtime mode: extension",
      "Distribution ID: company-extension",
      "Distribution version: 1.0.0",
    };
    private static final String[] UNEXPECTED_OPERATIONAL_OUTPUT = {
      " :: Spring Boot :: ",
      "Starting Seed4JCliApp",
      "[EXT-LOGBACK-OVERRIDE]",
      "[EXT-APPLICATION-OVERRIDE]",
      "Missing watchable .xml or .properties files",
      "Watching .xml files requires that the main configuration file is reachable as a URL",
    };

    @Test
    void shouldExecuteVersionCommandInExtensionMode() throws IOException {
      ExtensionCatalogFixture fixture = ExtensionCatalogFixture.withExtension();

      try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
        CliLaunchResult versionLaunch = fixture.launch("--version");

        assertThat(versionLaunch.exitCode()).isZero();
        assertThat(versionLaunch.output())
          .contains("Runtime mode: extension")
          .contains("Distribution ID: company-extension")
          .contains("Distribution version: 1.0.0");
        assertThat(System.getProperties())
          .containsEntry(RUNTIME_MODE_PROPERTY, "baseline-mode")
          .doesNotContainKeys(DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY);
      }
    }

    @Test
    void shouldKeepStandardCatalogAndAddOnlyTheExtensionOnlySlug() throws IOException {
      try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
        Comparison catalogs = ExtensionCatalogFixture.compareStandardWithListExtensionCatalogs();

        assertThat(catalogs.exitCodes()).containsExactly(0, 0);
        assertThat(catalogs.standardSlugs()).doesNotContain(EXTENSION_ONLY_SLUG).doesNotHaveDuplicates();
        assertThat(catalogs.extensionSlugs()).contains(EXTENSION_ONLY_SLUG).doesNotHaveDuplicates();
        assertThat(catalogs.addedSlugs()).containsExactly(EXTENSION_ONLY_SLUG);
        assertThat(catalogs.removedSlugs()).isEmpty();
        assertThat(System.getProperties())
          .containsEntry(RUNTIME_MODE_PROPERTY, "baseline-mode")
          .doesNotContainKeys(DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY);
      }
    }

    @Test
    void shouldCompleteExtensionOnlySlugInExtensionMode() throws IOException {
      ExtensionCatalogFixture fixture = ExtensionCatalogFixture.withListExtension();

      try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
        CliLaunchResult completionLaunch = fixture.launch("completion", "bash");

        assertThat(completionLaunch.exitCode()).isZero();
        assertThat(completionLaunch.output()).contains(EXTENSION_ONLY_SLUG);
        assertThat(System.getProperties())
          .containsEntry(RUNTIME_MODE_PROPERTY, "baseline-mode")
          .doesNotContainKeys(DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY);
      }
    }

    @Test
    void shouldListCustomPackageExtensionOnlySlug() throws IOException {
      ExtensionCatalogFixture fixture = ExtensionCatalogFixture.withCustomPackageListExtension();

      try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
        CliLaunchResult listLaunch = fixture.launch("list");

        assertThat(listLaunch.exitCode()).isZero();
        assertThat(ModuleCatalogOutput.slugsIn(listLaunch.output())).contains(CUSTOM_PACKAGE_EXTENSION_ONLY_SLUG).doesNotHaveDuplicates();
        assertThat(System.getProperties())
          .containsEntry(RUNTIME_MODE_PROPERTY, "baseline-mode")
          .doesNotContainKeys(DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY);
      }
    }

    @Test
    void shouldKeepCoreModulesVisibleWhenExtensionPublishesHiddenResourceOverrides() throws IOException {
      ExtensionCatalogFixture fixture = ExtensionCatalogFixture.withHiddenResourceOverrides();

      try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
        CliLaunchResult listLaunch = fixture.launch("list");

        assertThat(listLaunch.exitCode()).isZero();
        assertThat(listLaunch.output()).contains(EXTENSION_ONLY_SLUG).contains(CORE_SLUG_THAT_EXTENSION_TRIES_TO_HIDE);
        assertThat(System.getProperties())
          .containsEntry(RUNTIME_MODE_PROPERTY, "baseline-mode")
          .doesNotContainKeys(DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY);
      }
    }

    @Test
    void shouldKeepOperationalOutputCleanWhenExtensionPublishesLoggingOverrides() throws IOException {
      ExtensionCatalogFixture fixture = ExtensionCatalogFixture.withRegressionOverrides();

      try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
        VersionAndListLaunch launches = fixture.launchVersionAndList();

        assertThat(launches.exitCodes()).containsExactly(0, 0);
        assertThat(launches.version().output()).contains(EXPECTED_EXTENSION_VERSION_OUTPUT);
        assertThat(launches.list().output()).contains(EXTENSION_ONLY_SLUG);
        assertThat(launches.combinedOutput()).doesNotContain(UNEXPECTED_OPERATIONAL_OUTPUT);
        assertThat(System.getProperties())
          .containsEntry(RUNTIME_MODE_PROPERTY, "baseline-mode")
          .doesNotContainKeys(DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY);
      }
    }
  }

  @Nested
  class ExtensionApply {

    private static final String EXTENSION_SHARED_RUNTIME_APPLY_MODULE_SLUG = "runtime-extension-apply-shared-context";
    private static final String OVERRIDDEN_PRETTIER_VERSION = "3.6.2";
    private static final String OVERRIDDEN_PRETTIER_TEMPLATE_MARKER = "seed4j-extension-template-override";

    @Test
    void shouldApplyExtensionModuleUsingSharedRuntimeResources() throws IOException {
      SharedRuntimeApplyScenario scenario = SharedRuntimeApplyScenario.create();

      try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
        SharedRuntimeApplyResults results = scenario.execute();

        assertThat(results.exitCodes()).containsExactly(0, 0);
        assertThat(scenario.packageJsonContent()).contains("\"prettier\": \"" + OVERRIDDEN_PRETTIER_VERSION + "\"");
        assertThat(scenario.prettierConfiguration()).contains(OVERRIDDEN_PRETTIER_TEMPLATE_MARKER);
        assertThat(System.getProperties())
          .containsEntry(RUNTIME_MODE_PROPERTY, "baseline-mode")
          .doesNotContainKeys(DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY);
      }
    }

    @Test
    void shouldOverrideCorePrettierDependencyVersionsOnlyInExtensionMode() throws IOException {
      PrettierOverrideScenario scenario = PrettierOverrideScenario.create();

      try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
        PrettierOverrideResults results = scenario.execute();

        assertThat(results.exitCodes()).containsExactly(0, 0, 0, 0);
        assertThat(scenario.standardPackageJsonContent()).doesNotContain("\"prettier\": \"" + OVERRIDDEN_PRETTIER_VERSION + "\"");
        assertThat(scenario.extensionPackageJsonContent()).contains("\"prettier\": \"" + OVERRIDDEN_PRETTIER_VERSION + "\"");
        assertThat(System.getProperties())
          .containsEntry(RUNTIME_MODE_PROPERTY, "baseline-mode")
          .doesNotContainKeys(DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY);
      }
    }

    private static CliLaunchResult applyInit(PreSpringBootstrapRunner runner, Path projectPath) {
      return launchCapturingOutput(runner, ModuleApplyInvocation.init(projectPath).arguments());
    }

    private static CliLaunchResult applyPrettier(PreSpringBootstrapRunner runner, Path projectPath) {
      return launchCapturingOutput(runner, ModuleApplyInvocation.prettier(projectPath).arguments());
    }

    private static final class SharedRuntimeApplyScenario {

      private final Path projectPath;
      private final PreSpringBootstrapRunner runner;

      private SharedRuntimeApplyScenario(Path projectPath, PreSpringBootstrapRunner runner) {
        this.projectPath = projectPath;
        this.runner = runner;
      }

      private static SharedRuntimeApplyScenario create() throws IOException {
        Path userHome = Files.createTempDirectory("seed4j-cli-apply-shared-runtime-primary-");
        Path projectPath = Files.createTempDirectory("seed4j-cli-apply-shared-runtime-project-primary-");
        ExtensionRuntimeFixture.installWithApplyExtensionModuleUsingSharedRuntimeOverrides(userHome);
        return new SharedRuntimeApplyScenario(projectPath, InProcessChildRuntimeLauncher.runner(userHome));
      }

      private SharedRuntimeApplyResults execute() {
        CliLaunchResult initResult = applyInit(runner, projectPath);
        CliLaunchResult extensionModuleApplyResult = launchCapturingOutput(
          runner,
          "apply",
          EXTENSION_SHARED_RUNTIME_APPLY_MODULE_SLUG,
          "--project-path",
          projectPath.toString(),
          "--no-commit"
        );
        return new SharedRuntimeApplyResults(initResult, extensionModuleApplyResult);
      }

      private Path projectPath() {
        return projectPath;
      }

      private String packageJsonContent() throws IOException {
        return Files.readString(projectPath.resolve("package.json"));
      }

      private String prettierConfiguration() throws IOException {
        return Files.readString(projectPath.resolve(".prettierrc"));
      }
    }

    private record ModuleApplyInvocation(Path projectPath, String moduleSlug, List<String> additionalArguments) {
      private ModuleApplyInvocation {
        additionalArguments = List.copyOf(additionalArguments);
      }

      private static ModuleApplyInvocation init(Path projectPath) {
        return new ModuleApplyInvocation(projectPath, "init", List.of("--node-package-manager", "npm"));
      }

      private static ModuleApplyInvocation prettier(Path projectPath) {
        return new ModuleApplyInvocation(projectPath, "prettier", List.of());
      }

      private String[] arguments() {
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
        arguments.addAll(additionalArguments);
        return arguments.toArray(String[]::new);
      }
    }

    private static final class PrettierOverrideScenario {

      private final RuntimeProject standardRuntime;
      private final RuntimeProject extensionRuntime;

      private PrettierOverrideScenario(RuntimeProject standardRuntime, RuntimeProject extensionRuntime) {
        this.standardRuntime = standardRuntime;
        this.extensionRuntime = extensionRuntime;
      }

      private static PrettierOverrideScenario create() throws IOException {
        RuntimeProject standardRuntime = RuntimeProject.create("standard");
        RuntimeProject extensionRuntime = RuntimeProject.create("extension");
        ExtensionRuntimeFixture.installWithApplyCommonSourceOverrideExtensionModule(extensionRuntime.userHome());
        return new PrettierOverrideScenario(standardRuntime, extensionRuntime);
      }

      private PrettierOverrideResults execute() throws IOException {
        return new PrettierOverrideResults(execute(standardRuntime), execute(extensionRuntime));
      }

      private RuntimeApplyResults execute(RuntimeProject runtimeProject) throws IOException {
        PreSpringBootstrapRunner runner = InProcessChildRuntimeLauncher.runner(runtimeProject.userHome());
        CliLaunchResult initResult = applyInit(runner, runtimeProject.projectPath());
        CliLaunchResult prettierResult = applyPrettier(runner, runtimeProject.projectPath());
        return new RuntimeApplyResults(initResult, prettierResult);
      }

      private String standardPackageJsonContent() throws IOException {
        return Files.readString(standardRuntime.projectPath().resolve("package.json"));
      }

      private String extensionPackageJsonContent() throws IOException {
        return Files.readString(extensionRuntime.projectPath().resolve("package.json"));
      }
    }

    private record RuntimeProject(Path userHome, Path projectPath) {
      private static RuntimeProject create(String runtimeName) throws IOException {
        return new RuntimeProject(
          Files.createTempDirectory("seed4j-cli-apply-common-" + runtimeName + "-primary-"),
          Files.createTempDirectory("seed4j-cli-apply-common-" + runtimeName + "-project-primary-")
        );
      }
    }

    private record RuntimeApplyResults(CliLaunchResult init, CliLaunchResult prettier) {}

    private record PrettierOverrideResults(RuntimeApplyResults standard, RuntimeApplyResults extension) {
      private List<Integer> exitCodes() {
        return List.of(
          standard.init().exitCode(),
          extension.init().exitCode(),
          standard.prettier().exitCode(),
          extension.prettier().exitCode()
        );
      }
    }

    private record SharedRuntimeApplyResults(CliLaunchResult init, CliLaunchResult extensionModuleApply) {
      private List<Integer> exitCodes() {
        return List.of(init.exitCode(), extensionModuleApply.exitCode());
      }
    }
  }
}
