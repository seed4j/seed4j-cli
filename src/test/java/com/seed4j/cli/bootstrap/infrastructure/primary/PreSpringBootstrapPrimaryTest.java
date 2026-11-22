package com.seed4j.cli.bootstrap.infrastructure.primary;

import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.runtimeConfigurationPath;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.writeConfiguration;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.writeRuntimeConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliExecutablePath;
import com.seed4j.cli.bootstrap.infrastructure.primary.PrimaryBootstrapFixture.BootstrapLaunch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
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
        new Seed4JCliArguments(new String[] { "--version" })
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
    void shouldRejectLegacyConfigurationAndPreserveTheInstalledRuntime() throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-legacy-extension-");
      writeRuntimeConfiguration(userHome, "seed4j:\n  runtime:\n    mode: extension\n");
      Path config = runtimeConfigurationPath(userHome);
      Path installedJar = Files.createDirectories(config.getParent().resolve("runtime/active")).resolve("extension.jar");
      try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(installedJar))) {
        jar.putNextEntry(new JarEntry("BOOT-INF/classes/"));
        jar.closeEntry();
      }
      String originalConfig = Files.readString(config);
      byte[] originalJar = Files.readAllBytes(installedJar);
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

      BootstrapLaunch launch = fixture.launch("--version");

      assertThat(launch.result().exitCode()).isEqualTo(1);
      assertThat(launch.result().output()).contains("seed4j.runtime.mode: standard", "~/.config/seed4j-cli/config.yml");
      assertThat(fixture.localRunArguments()).isEmpty();
      assertThat(launch.childRuntimeRequest()).isNull();
      assertThat(Files.readString(config)).isEqualTo(originalConfig);
      assertThat(Files.readAllBytes(installedJar)).isEqualTo(originalJar);
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
      assertThat(fixture.localRunArguments()).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("standardMode")
    void shouldLaunchTheStandardChildRuntimeWhenConfigDoesNotSelectExtensionMode(
      String scenarioName,
      String configContent
    ) throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-standard-config-primary-");
      writeRuntimeConfiguration(userHome, configContent);
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

      BootstrapLaunch launch = fixture.launch("--version");
      ChildRuntimeLaunchRequest childLaunchRequest = launch.childRuntimeRequest();

      assertThat(launch.result().exitCode()).isZero();
      assertThat(childLaunchRequest).isNotNull();
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
          "runtime mode set to standard with surrounding spaces and mixed case",
          """
          seed4j:
            runtime:
              mode: "  Standard  "
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
    @MethodSource("unsupportedModes")
    void shouldExplainUnsupportedRuntimeModeBeforeLaunchingAnything(String configuredMode, String expectedMessage) throws IOException {
      Path userHome = Files.createTempDirectory("seed4j-cli-unsupported-mode-primary-");
      writeRuntimeConfiguration(userHome, "seed4j:\n  runtime:\n    mode: " + configuredMode + "\n");
      PrimaryBootstrapFixture fixture = PrimaryBootstrapFixture.packaged(userHome);

      BootstrapLaunch launch = fixture.launch("--version");

      assertThat(launch.result().exitCode()).isEqualTo(1);
      assertThat(launch.result().output()).contains(expectedMessage);
      assertThat(fixture.childLaunchRequest()).isNull();
      assertThat(fixture.localRunArguments()).isEmpty();
    }

    static Stream<Arguments> unsupportedModes() {
      return Stream.of(
        Arguments.of(
          "extension",
          "Loading runtime extension JARs is no longer supported. Set seed4j.runtime.mode: standard in ~/.config/seed4j-cli/config.yml."
        ),
        Arguments.of(
          "EXTENSION",
          "Loading runtime extension JARs is no longer supported. Set seed4j.runtime.mode: standard in ~/.config/seed4j-cli/config.yml."
        ),
        Arguments.of(
          "corporate",
          "Invalid ~/.config/seed4j-cli/config.yml: Invalid seed4j.runtime.mode 'corporate'. Valid value: standard."
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
  }
}
