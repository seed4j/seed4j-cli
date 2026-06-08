package com.seed4j.cli.bootstrap.composition;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

@UnitTest
class PreSpringBootstrapConfigurationTest {

  private static final String BOOTSTRAP_DOMAIN_LOGGER_NAME = "com.seed4j.cli.bootstrap.domain";

  @TempDir
  private Path userHome;

  @Test
  void shouldEnableBootstrapDiagnosticsBeforeReportingInvalidExtensionRuntime() throws IOException {
    Path executableJar = userHome.resolve("seed4j-cli.jar");
    Files.createFile(executableJar);
    createExtensionRuntimeWithoutStartClass(userHome);
    Logger bootstrapDomainLogger = bootstrapDomainLogger();
    Level previousLevel = bootstrapDomainLogger.getLevel();
    bootstrapDomainLogger.setLevel(Level.INFO);
    PreSpringBootstrapRunner runner = PreSpringBootstrapConfiguration.preSpringBootstrapRunner(
      new PreSpringRuntimeEnvironment(new Seed4JCliHome(userHome), executableJar, false, Path.of("/usr/bin/java"))
    );

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = runner.exitCodeFor(new String[] { "--version", "--debug" });

      assertThat(exitCode).isNotZero();
      assertThat(outputCaptor.getStandardError()).contains("Invalid runtime jar file:").contains("Missing manifest Start-Class.");
      assertThat(bootstrapDomainLogger.getLevel()).isEqualTo(Level.DEBUG);
    } finally {
      bootstrapDomainLogger.setLevel(previousLevel);
    }
  }

  private static void createExtensionRuntimeWithoutStartClass(Path userHome) throws IOException {
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeDirectory = userHome.resolve(".config/seed4j-cli/runtime/active");
    Files.createDirectories(runtimeDirectory);
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: extension
      """
    );
    Files.writeString(
      runtimeDirectory.resolve("metadata.yml"),
      """
      distribution:
        id: company-extension
        version: 1.0.0
      """
    );
    createJarWithoutStartClass(runtimeDirectory.resolve("extension.jar"));
  }

  private static void createJarWithoutStartClass(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
    }
  }

  private static Logger bootstrapDomainLogger() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    return loggerContext.getLogger(BOOTSTRAP_DOMAIN_LOGGER_NAME);
  }
}
