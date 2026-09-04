package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.BootstrapDebugMode;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliExecutablePath;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture;
import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture.ExtensionRuntimeFixturePaths;
import com.seed4j.cli.bootstrap.infrastructure.primary.PrimaryBootstrapFixture.BootstrapLaunch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringBootstrapPrimaryTest {

  @Test
  void shouldLaunchTheStandardChildRuntimeWithArguments() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-standard-child-primary-");
    PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);
    ChildRuntimeLaunchRequest expectedRequest = new ChildRuntimeLaunchRequest(
      new Seed4JCliExecutablePath(fixture.executablePath()),
      RuntimeSelection.standard(),
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
      RuntimeSelection.extension(
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
      RuntimeSelection.extension(
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
