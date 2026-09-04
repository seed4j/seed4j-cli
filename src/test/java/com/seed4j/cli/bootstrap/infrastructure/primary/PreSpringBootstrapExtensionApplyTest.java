package com.seed4j.cli.bootstrap.infrastructure.primary;

import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.assertBaselineRuntimePropertiesRestored;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.baselineRuntimeProperties;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.launchCapturingOutput;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.CliLaunchResult;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.ScopedRuntimeProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringBootstrapExtensionApplyTest {

  private static final String EXTENSION_SHARED_RUNTIME_APPLY_MODULE_SLUG = "runtime-extension-apply-shared-context";
  private static final String OVERRIDDEN_PRETTIER_VERSION = "3.6.2";
  private static final String OVERRIDDEN_PRETTIER_TEMPLATE_MARKER = "seed4j-extension-template-override";

  @Test
  void shouldApplyExtensionModuleUsingSharedRuntimeResources() throws IOException {
    SharedRuntimeApplyScenario scenario = SharedRuntimeApplyScenario.create();

    try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
      SharedRuntimeApplyResults results = scenario.execute();

      assertThat(results.init().exitCode()).isZero();
      assertThat(results.extensionModuleApply().exitCode())
        .withFailMessage("Expected extension apply module command to succeed but got output:%n%s", results.extensionModuleApply().output())
        .isZero();
      assertThat(Files.readString(scenario.projectPath().resolve("package.json"))).contains(
        "\"prettier\": \"" + OVERRIDDEN_PRETTIER_VERSION + "\""
      );
      assertThat(Files.readString(scenario.projectPath().resolve(".prettierrc"))).contains(OVERRIDDEN_PRETTIER_TEMPLATE_MARKER);
      assertBaselineRuntimePropertiesRestored();
    }
  }

  @Test
  void shouldOverrideCorePrettierDependencyVersionsOnlyInExtensionMode() throws IOException {
    PrettierOverrideScenario scenario = PrettierOverrideScenario.create();

    try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
      PrettierOverrideResults results = scenario.execute();

      assertThat(results.standard().init().exitCode()).isZero();
      assertThat(results.extension().init().exitCode())
        .withFailMessage("Expected extension init command to succeed but got output:%n%s", results.extension().init().output())
        .isZero();
      assertThat(results.standard().prettier().exitCode()).isZero();
      assertThat(results.extension().prettier().exitCode())
        .withFailMessage("Expected extension prettier command to succeed but got output:%n%s", results.extension().prettier().output())
        .isZero();
      assertThat(Files.readString(scenario.standardProjectPath())).doesNotContain("\"prettier\": \"" + OVERRIDDEN_PRETTIER_VERSION + "\"");
      assertThat(Files.readString(scenario.extensionProjectPath())).contains("\"prettier\": \"" + OVERRIDDEN_PRETTIER_VERSION + "\"");
      assertBaselineRuntimePropertiesRestored();
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

    private Path standardProjectPath() {
      return standardRuntime.projectPath().resolve("package.json");
    }

    private Path extensionProjectPath() {
      return extensionRuntime.projectPath().resolve("package.json");
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

  private record PrettierOverrideResults(RuntimeApplyResults standard, RuntimeApplyResults extension) {}

  private record SharedRuntimeApplyResults(CliLaunchResult init, CliLaunchResult extensionModuleApply) {}
}
