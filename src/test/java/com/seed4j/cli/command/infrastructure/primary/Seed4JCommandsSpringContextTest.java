package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import picocli.CommandLine;

@UnitTest
class Seed4JCommandsSpringContextTest {

  private static final String DISTRIBUTION_ID = "company-extension";
  private static final String DISTRIBUTION_VERSION = "1.0.0";

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(
    ExtensionInstallSpringContextConfiguration.class
  );

  @Test
  void shouldInstallExtensionRuntimeUsingSpringManagedCommandGraph() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-spring-context-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    String[] args = {
      "extension",
      "install",
      extensionJarPath.toString(),
      "--distribution-id",
      DISTRIBUTION_ID,
      "--distribution-version",
      DISTRIBUTION_VERSION,
    };

    contextRunner
      .withPropertyValues("user.home=" + userHome)
      .run(context -> {
        Seed4JCommandsFactory commandsFactory = context.getBean(Seed4JCommandsFactory.class);

        try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
          CommandLine commandLine = new CommandLine(commandsFactory.buildCommandSpec());
          int exitCode = commandLine.execute(args);

          assertThat(exitCode).isZero();
          assertThat(outputCaptor.getStandardOutput()).contains("Extension runtime installed successfully.");
        }

        assertThat(configPath).exists();
        assertThat(Files.readString(configPath)).contains("mode: extension");
        assertThat(runtimeJarPath).exists();
        assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(Files.readAllBytes(extensionJarPath));
        assertThat(metadataPath).exists();
        assertThat(Files.readString(metadataPath)).contains("id: " + DISTRIBUTION_ID).contains("version: " + DISTRIBUTION_VERSION);
      });
  }

  @Test
  void shouldReturnNonZeroAndShowObjectiveErrorWhenRuntimeConfigIsInvalidUsingSpringManagedCommandGraph() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-extension-install-spring-context-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
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

    contextRunner
      .withPropertyValues("user.home=" + userHome)
      .run(context -> {
        Seed4JCommandsFactory commandsFactory = context.getBean(Seed4JCommandsFactory.class);

        try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
          CommandLine commandLine = new CommandLine(commandsFactory.buildCommandSpec());
          int exitCode = commandLine.execute(args);

          assertThat(exitCode).isNotZero();
          assertThat(outputCaptor.getStandardError())
            .contains("Invalid ~/.config/seed4j-cli/config.yml")
            .contains("seed4j.runtime.mode must be a string");
          assertThat(outputCaptor.getStandardOutput()).doesNotContain("Extension runtime installed successfully.");
        }
      });
  }

  @Test
  void shouldShowStandardRuntimeInVersionOutputUsingSpringManagedCommandGraph() {
    String[] args = { "--version" };

    contextRunner.run(context -> {
      Seed4JCommandsFactory commandsFactory = context.getBean(Seed4JCommandsFactory.class);

      try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
        CommandLine commandLine = new CommandLine(commandsFactory.buildCommandSpec());
        int exitCode = commandLine.execute(args);

        assertThat(exitCode).isZero();
        assertThat(outputCaptor.getStandardOutput())
          .contains("Runtime mode: standard")
          .doesNotContain("Distribution ID")
          .doesNotContain("Distribution version");
      }
    });
  }

  @Test
  void shouldShowExtensionRuntimeDistributionInVersionOutputUsingSpringManagedCommandGraph() {
    String[] args = { "--version" };

    contextRunner
      .withPropertyValues(
        "seed4j.cli.runtime.mode=extension",
        "seed4j.cli.runtime.distribution.id=" + DISTRIBUTION_ID,
        "seed4j.cli.runtime.distribution.version=" + DISTRIBUTION_VERSION
      )
      .run(context -> {
        Seed4JCommandsFactory commandsFactory = context.getBean(Seed4JCommandsFactory.class);

        try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
          CommandLine commandLine = new CommandLine(commandsFactory.buildCommandSpec());
          int exitCode = commandLine.execute(args);

          assertThat(exitCode).isZero();
          assertThat(outputCaptor.getStandardOutput())
            .contains("Runtime mode: extension")
            .contains("Distribution ID: " + DISTRIBUTION_ID)
            .contains("Distribution version: " + DISTRIBUTION_VERSION);
        }
      });
  }

  private static Path createFatJar(Path jarPath) throws IOException {
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

  @Configuration
  @ComponentScan(
    basePackages = { "com.seed4j.cli.command", "com.seed4j.cli.bootstrap" },
    useDefaultFilters = false,
    includeFilters = {
      @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = { Seed4JCommandsFactory.class, ExtensionCommand.class, ExtensionInstallCommand.class }
      ),
      @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = {
          "com\\.seed4j\\.cli\\.command\\.application\\.RuntimeDisplayApplicationService",
          "com\\.seed4j\\.cli\\.command\\.application\\.RuntimeExtensionInstallApplicationService",
          "com\\.seed4j\\.cli\\.command\\.infrastructure\\.secondary\\.BootstrapRuntimeDisplayReader",
          "com\\.seed4j\\.cli\\.command\\.infrastructure\\.secondary\\.BootstrapRuntimeExtensionInstaller",
          "com\\.seed4j\\.cli\\.bootstrap\\.application\\.RuntimeExtensionApplicationService",
          "com\\.seed4j\\.cli\\.bootstrap\\.infrastructure\\.primary\\.JavaRuntimeExtensionInstaller",
          "com\\.seed4j\\.cli\\.bootstrap\\.infrastructure\\.primary\\.JavaRuntimeSelectionReader",
          "com\\.seed4j\\.cli\\.bootstrap\\.infrastructure\\.secondary\\.RuntimeExtensionSpringConfiguration",
          "com\\.seed4j\\.cli\\.bootstrap\\.infrastructure\\.secondary\\.RuntimeSelectionConfiguration",
        }
      ),
    }
  )
  static class ExtensionInstallSpringContextConfiguration {}
}
