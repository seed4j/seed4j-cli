package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import picocli.CommandLine;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
class ExtensionRuntimeCommandsTest {

  private static final String DISTRIBUTION_ID = "company-extension";
  private static final String DISTRIBUTION_VERSION = "1.0.0";
  private static final Path USER_HOME = temporaryDirectory();

  @Autowired
  private Seed4JCommandsFactory commandsFactory;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("user.home", USER_HOME::toString);
  }

  @BeforeEach
  void cleanUserHomeConfiguration() throws IOException {
    deleteRecursively(USER_HOME.resolve(".config/seed4j-cli"));
  }

  @Test
  void shouldInstallExtensionRuntime(CapturedOutput output) throws IOException {
    Path extensionJarPath = createFatJar(USER_HOME.resolve("company-extension.jar"));
    Path configPath = USER_HOME.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = USER_HOME.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = USER_HOME.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    String[] args = {
      "extension",
      "install",
      extensionJarPath.toString(),
      "--distribution-id",
      DISTRIBUTION_ID,
      "--distribution-version",
      DISTRIBUTION_VERSION,
    };

    int exitCode = commandLine().execute(args);

    assertThat(exitCode).isZero();
    assertThat(output.getOut()).contains("Extension runtime installed successfully.");
    assertThat(configPath).exists();
    assertThat(Files.readString(configPath)).contains("mode: extension");
    assertThat(runtimeJarPath).exists();
    assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(metadataPath).exists();
    assertThat(Files.readString(metadataPath)).contains("id: " + DISTRIBUTION_ID).contains("version: " + DISTRIBUTION_VERSION);
  }

  @Test
  void shouldReplaceActiveExtensionRuntime(CapturedOutput output) throws IOException {
    Path extensionJarPath = createFatJar(
      USER_HOME.resolve("company-extension.jar"),
      "BOOT-INF/classes/com/company/New.class",
      new byte[] { 2, 3 }
    );
    Path runtimeJarPath = USER_HOME.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = USER_HOME.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(runtimeJarPath.getParent());
    createFatJar(runtimeJarPath, "BOOT-INF/classes/com/company/Legacy.class", new byte[] { 1 });
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: legacy-extension
        version: 0.9.0
      """
    );
    String[] args = {
      "extension",
      "install",
      extensionJarPath.toString(),
      "--distribution-id",
      DISTRIBUTION_ID,
      "--distribution-version",
      DISTRIBUTION_VERSION,
    };

    int exitCode = commandLine().execute(args);

    assertThat(exitCode).isZero();
    assertThat(output.getOut()).contains("Replaced active runtime extension.").contains("Extension runtime installed successfully.");
    assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(Files.readString(metadataPath)).contains("id: " + DISTRIBUTION_ID).contains("version: " + DISTRIBUTION_VERSION);
  }

  @Test
  void shouldEnableExtensionRuntime(CapturedOutput output) throws IOException {
    Path configPath = USER_HOME.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = USER_HOME.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = USER_HOME.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(configPath.getParent());
    Files.createDirectories(runtimeJarPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
    createFatJar(runtimeJarPath);
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: company-extension
        version: 1.0.0
      """
    );
    String[] args = { "extension", "enable" };

    int exitCode = commandLine().execute(args);

    assertThat(exitCode).isZero();
    assertThat(output.getOut()).contains("Extension runtime enabled successfully.").contains("Config: " + configPath);
    assertThat(Files.readString(configPath)).contains("mode: extension");
  }

  @Test
  void shouldReturnNonZeroAndNotChangeConfigWhenEnablingInvalidExtensionRuntime(CapturedOutput output) throws IOException {
    Path configPath = USER_HOME.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = USER_HOME.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Files.createDirectories(configPath.getParent());
    Files.createDirectories(runtimeJarPath.getParent());
    String originalConfig = """
      seed4j:
        runtime:
          mode: standard
      """;
    Files.writeString(configPath, originalConfig);
    createFatJar(runtimeJarPath);
    String[] args = { "extension", "enable" };

    int exitCode = commandLine().execute(args);

    assertThat(exitCode).isNotZero();
    assertThat(output.getErr()).contains("Invalid runtime metadata file");
    assertThat(output.getOut()).doesNotContain("Extension runtime enabled successfully.");
    assertThat(Files.readString(configPath)).isEqualTo(originalConfig);
  }

  @Test
  void shouldDisableExtensionRuntimeAndPreserveArtifacts(CapturedOutput output) throws IOException {
    Path configPath = USER_HOME.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = USER_HOME.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = USER_HOME.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(runtimeJarPath.getParent());
    createFatJar(runtimeJarPath);
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: company-extension
        version: 1.0.0
      """
    );
    byte[] runtimeJarContentBeforeDisable = Files.readAllBytes(runtimeJarPath);
    String metadataContentBeforeDisable = Files.readString(metadataPath);
    String[] args = { "extension", "disable" };

    int exitCode = commandLine().execute(args);

    assertThat(exitCode).isZero();
    assertThat(output.getOut()).contains("Extension runtime disabled successfully.").contains("Config: " + configPath);
    assertThat(configPath).exists();
    assertThat(Files.readString(configPath)).contains("mode: standard");
    assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(runtimeJarContentBeforeDisable);
    assertThat(Files.readString(metadataPath)).isEqualTo(metadataContentBeforeDisable);
  }

  @Test
  void shouldReturnNonZeroAndPreserveInvalidConfigWhenDisabling(CapturedOutput output) throws IOException {
    Path configPath = USER_HOME.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j: [broken");
    String[] args = { "extension", "disable" };

    int exitCode = commandLine().execute(args);

    assertThat(exitCode).isNotZero();
    assertThat(output.getErr()).contains("Could not read ~/.config/seed4j-cli/config.yml.").contains("Details:");
    assertThat(output.getOut()).doesNotContain("Extension runtime disabled successfully.");
    assertThat(Files.readString(configPath)).isEqualTo("seed4j: [broken");
  }

  @Test
  void shouldReturnNonZeroAndShowObjectiveErrorWhenRuntimeConfigIsInvalid(CapturedOutput output) throws IOException {
    Path extensionJarPath = createFatJar(USER_HOME.resolve("company-extension.jar"));
    Path configPath = USER_HOME.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: 42
      """
    );
    String[] args = {
      "extension",
      "install",
      extensionJarPath.toString(),
      "--distribution-id",
      DISTRIBUTION_ID,
      "--distribution-version",
      DISTRIBUTION_VERSION,
    };

    int exitCode = commandLine().execute(args);

    assertThat(exitCode).isNotZero();
    assertThat(output.getErr()).contains("Invalid ~/.config/seed4j-cli/config.yml").contains("seed4j.runtime.mode must be a string");
    assertThat(output.getOut()).doesNotContain("Extension runtime installed successfully.");
  }

  @Test
  void shouldShowStandardRuntimeInVersionOutput(CapturedOutput output) {
    String[] args = { "--version" };

    int exitCode = commandLine().execute(args);

    assertThat(exitCode).isZero();
    assertThat(output.getOut()).contains("Runtime mode: standard").doesNotContain("Distribution ID").doesNotContain("Distribution version");
  }

  private CommandLine commandLine() {
    return new CommandLine(commandsFactory.buildCommandSpec());
  }

  private static Path createFatJar(Path jarPath) throws IOException {
    return createFatJar(jarPath, "BOOT-INF/classes/", new byte[] {});
  }

  private static Path createFatJar(Path jarPath, String additionalEntryName, byte[] additionalEntryContent) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
      if (!"BOOT-INF/classes/".equals(additionalEntryName)) {
        jarOutputStream.putNextEntry(new JarEntry(additionalEntryName));
        jarOutputStream.write(additionalEntryContent);
        jarOutputStream.closeEntry();
      }
    }

    return jarPath;
  }

  private static Path temporaryDirectory() {
    try {
      return Files.createTempDirectory("seed4j-cli-spring-context-");
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (Files.notExists(path)) {
      return;
    }

    try (Stream<Path> paths = Files.walk(path)) {
      for (Path currentPath : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(currentPath);
      }
    }
  }
}
