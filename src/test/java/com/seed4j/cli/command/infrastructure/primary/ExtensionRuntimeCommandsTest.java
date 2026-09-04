package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import com.seed4j.cli.command.infrastructure.primary.ExtensionRuntimeCommandsFixture.ActiveRuntimeArtifacts;
import com.seed4j.cli.command.infrastructure.primary.ExtensionRuntimeCommandsFixture.ExtensionRuntimePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@IntegrationTest
class ExtensionRuntimeCommandsTest {

  private static final Path USER_HOME = ExtensionRuntimeCommandsFixture.temporaryUserHome();

  @Autowired
  private Seed4JCommandsFactory commandsFactory;

  private ExtensionRuntimeCommandsFixture fixture;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("user.home", USER_HOME::toString);
  }

  @BeforeEach
  void prepareFixture() throws IOException {
    fixture = new ExtensionRuntimeCommandsFixture(USER_HOME, commandsFactory);
    fixture.cleanUserHomeConfiguration();
  }

  @Nested
  class Install {

    @Test
    void shouldInstallExtensionRuntime(CapturedOutput output) throws IOException {
      Path extensionJarPath = fixture.createFatJar(USER_HOME.resolve("company-extension.jar"));
      ExtensionRuntimePaths runtimePaths = fixture.runtimePaths();

      int exitCode = fixture.commandLine().execute(fixture.installArguments(extensionJarPath));

      assertThat(exitCode).isZero();
      assertThat(output.getOut())
        .contains("Extension runtime installed successfully.")
        .containsSubsequence("Validate installation with:", "  seed4j --version", "  seed4j list")
        .doesNotContain("Replaced active runtime extension.");
      assertThat(Files.readString(runtimePaths.configPath())).contains("mode: extension");
      assertThat(Files.readAllBytes(runtimePaths.runtimeJarPath())).isEqualTo(Files.readAllBytes(extensionJarPath));
      assertThat(runtimePaths.metadataPath()).exists();
      assertThat(Files.readString(runtimePaths.metadataPath())).contains("id: company-extension").contains("version: 1.0.0");
    }

    @Test
    void shouldReplaceActiveExtensionRuntime(CapturedOutput output) throws IOException {
      Path extensionJarPath = fixture.createFatJarWithClass(
        USER_HOME.resolve("company-extension.jar"),
        "BOOT-INF/classes/com/company/New.class",
        new byte[] { 2, 3 }
      );
      ExtensionRuntimePaths runtimePaths = fixture.runtimePaths();
      Files.createDirectories(runtimePaths.runtimeJarPath().getParent());
      fixture.createFatJarWithClass(runtimePaths.runtimeJarPath(), "BOOT-INF/classes/com/company/Legacy.class", new byte[] { 1 });
      fixture.writeRuntimeMetadata(runtimePaths.metadataPath(), "legacy-extension", "0.9.0");

      int exitCode = fixture.commandLine().execute(fixture.installArguments(extensionJarPath));

      assertThat(exitCode).isZero();
      assertThat(output.getOut()).contains("Replaced active runtime extension.").contains("Extension runtime installed successfully.");
      assertThat(Files.readAllBytes(runtimePaths.runtimeJarPath())).isEqualTo(Files.readAllBytes(extensionJarPath));
      assertThat(Files.readString(runtimePaths.metadataPath())).contains("id: company-extension").contains("version: 1.0.0");
    }

    @Test
    void shouldReturnNonZeroAndShowObjectiveErrorWhenRuntimeConfigIsInvalid(CapturedOutput output) throws IOException {
      Path extensionJarPath = fixture.createFatJar(USER_HOME.resolve("company-extension.jar"));
      ExtensionRuntimePaths runtimePaths = fixture.runtimePaths();
      fixture.writeRuntimeMode(runtimePaths.configPath(), "42");

      int exitCode = fixture.commandLine().execute(fixture.installArguments(extensionJarPath));

      assertThat(exitCode).isNotZero();
      assertThat(output.getErr()).contains("Invalid ~/.config/seed4j-cli/config.yml").contains("seed4j.runtime.mode must be a string");
      assertThat(output.getOut()).doesNotContain("Extension runtime installed successfully.");
    }
  }

  @Nested
  class Enable {

    @Test
    void shouldEnableExtensionRuntime(CapturedOutput output) throws IOException {
      ExtensionRuntimePaths runtimePaths = fixture.runtimePaths();
      fixture.writeRuntimeMode(runtimePaths.configPath(), "standard");
      fixture.installActiveRuntime(runtimePaths);

      int exitCode = fixture.commandLine().execute("extension", "enable");

      assertThat(exitCode).isZero();
      assertThat(output.getOut())
        .contains("Extension runtime enabled successfully.")
        .contains("Config: " + runtimePaths.configPath());
      assertThat(Files.readString(runtimePaths.configPath())).contains("mode: extension");
    }

    @Test
    void shouldReturnNonZeroAndNotChangeConfigWhenEnablingInvalidExtensionRuntime(CapturedOutput output) throws IOException {
      ExtensionRuntimePaths runtimePaths = fixture.runtimePaths();
      String originalConfig = fixture.writeRuntimeMode(runtimePaths.configPath(), "standard");
      fixture.installRuntimeWithoutMetadata(runtimePaths);

      int exitCode = fixture.commandLine().execute("extension", "enable");

      assertThat(exitCode).isNotZero();
      assertThat(output.getErr()).contains("Invalid runtime metadata file");
      assertThat(output.getOut()).doesNotContain("Extension runtime enabled successfully.");
      assertThat(Files.readString(runtimePaths.configPath())).isEqualTo(originalConfig);
    }
  }

  @Nested
  class Disable {

    @Test
    void shouldDisableExtensionRuntimeAndPreserveArtifacts(CapturedOutput output) throws IOException {
      ExtensionRuntimePaths runtimePaths = fixture.runtimePaths();
      ActiveRuntimeArtifacts activeRuntimeArtifacts = fixture.installActiveRuntime(runtimePaths);

      int exitCode = fixture.commandLine().execute("extension", "disable");

      assertThat(exitCode).isZero();
      assertThat(output.getOut())
        .contains("Extension runtime disabled successfully.")
        .contains("Config: " + runtimePaths.configPath());
      assertThat(Files.readString(runtimePaths.configPath())).contains("mode: standard");
      assertThat(Files.readAllBytes(runtimePaths.runtimeJarPath())).isEqualTo(activeRuntimeArtifacts.jarContent());
      assertThat(Files.readString(runtimePaths.metadataPath())).isEqualTo(activeRuntimeArtifacts.metadataContent());
    }

    @Test
    void shouldReturnNonZeroAndPreserveInvalidConfigWhenDisabling(CapturedOutput output) throws IOException {
      ExtensionRuntimePaths runtimePaths = fixture.runtimePaths();
      Files.createDirectories(runtimePaths.configPath().getParent());
      Files.writeString(runtimePaths.configPath(), "seed4j: [broken");

      int exitCode = fixture.commandLine().execute("extension", "disable");

      assertThat(exitCode).isNotZero();
      assertThat(output.getErr()).contains("Could not read ~/.config/seed4j-cli/config.yml.").contains("Details:");
      assertThat(output.getOut()).doesNotContain("Extension runtime disabled successfully.");
      assertThat(Files.readString(runtimePaths.configPath())).isEqualTo("seed4j: [broken");
    }
  }

  @Nested
  class Version {

    @Test
    void shouldShowStandardRuntimeInVersionOutput(CapturedOutput output) {
      int exitCode = fixture.commandLine().execute("--version");

      assertThat(exitCode).isZero();
      assertThat(output.getOut())
        .contains("Runtime mode: standard")
        .doesNotContain("Distribution ID")
        .doesNotContain("Distribution version");
    }
  }
}
