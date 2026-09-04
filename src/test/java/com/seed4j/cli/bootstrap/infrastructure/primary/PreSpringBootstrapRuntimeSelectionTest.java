package com.seed4j.cli.bootstrap.infrastructure.primary;

import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.assertBaselineRuntimePropertiesRestored;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.baselineRuntimeProperties;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.launchCapturingOutput;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.runtimeConfigurationPath;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.writeConfiguration;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.writeRuntimeConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.CliLaunchResult;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.ScopedRuntimeProperties;
import com.seed4j.cli.bootstrap.infrastructure.primary.PrimaryBootstrapFixture.BootstrapLaunch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class PreSpringBootstrapRuntimeSelectionTest {

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
    assertThat(childLaunchRequest.runtimeSelection()).isEqualTo(RuntimeSelection.standard());
    assertThat(fixture.localRunArguments()).isEmpty();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRuntimeConfigurations#standardMode")
  void shouldLaunchTheStandardChildRuntimeWhenConfigDoesNotSelectExtensionMode(String scenarioName, String configContent)
    throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-standard-config-primary-");
    writeRuntimeConfiguration(userHome, configContent);
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

    BootstrapLaunch launch = fixture.launch("--version");
    ChildRuntimeLaunchRequest childLaunchRequest = launch.childRuntimeRequest();

    assertThat(launch.result().exitCode()).isZero();
    assertThat(childLaunchRequest).isNotNull();
    assertThat(childLaunchRequest.runtimeSelection()).isEqualTo(RuntimeSelection.standard());
    assertThat(childLaunchRequest.debug().enabled()).isFalse();
    assertThat(fixture.localRunArguments()).isEmpty();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRuntimeConfigurations#invalid")
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
      assertBaselineRuntimePropertiesRestored();
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
