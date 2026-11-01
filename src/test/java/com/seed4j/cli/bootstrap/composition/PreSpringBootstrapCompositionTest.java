package com.seed4j.cli.bootstrap.composition;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.ComponentTest;
import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.bootstrap.domain.JavaExecutablePath;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.RuntimeProcessMode;
import com.seed4j.cli.bootstrap.domain.Seed4JCliExecutablePath;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.primary.ModuleCatalogOutput;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@ComponentTest
class PreSpringBootstrapCompositionTest {

  @TempDir
  private Path userHome;

  @TempDir
  private Path executablePath;

  @Test
  void shouldRunStandardRuntimeLocallyWhenExecutableIsNotPackaged() throws IOException {
    createStandardRuntimeConfiguration();
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      new Seed4JCliHome(userHome),
      new Seed4JCliExecutablePath(executablePath),
      RuntimeProcessMode.PARENT,
      new JavaExecutablePath(Path.of("unused-java"))
    );
    PreSpringBootstrapRunner runner = PreSpringBootstrapConfiguration.preSpringBootstrapRunner(runtimeEnvironment);

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = runner.exitCodeFor(new String[] { "--version" });

      assertThat(exitCode).isZero();
      assertThat(outputCaptor.getStandardError()).contains(
        "Standard mode is not running from a packaged CLI JAR. Falling back to local execution."
      );
      assertThat(outputCaptor.getStandardOutput())
        .contains("Seed4J CLI v")
        .contains("Seed4J version:")
        .contains("Runtime mode: standard")
        .doesNotContain("Distribution ID")
        .doesNotContain("Distribution version");
    }
  }

  @Test
  void shouldLoadExternalConfigurationFileWhenRunningStandardRuntimeLocally() throws IOException {
    Files.createDirectories(userHome.resolve(".config/seed4j-cli"));
    Files.copy(
      Path.of("src/test/resources/cli/config/seed4j-cli-hidden-resources.yml"),
      userHome.resolve(".config/seed4j-cli/config.yml")
    );
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      new Seed4JCliHome(userHome),
      new Seed4JCliExecutablePath(executablePath),
      RuntimeProcessMode.PARENT,
      new JavaExecutablePath(Path.of("unused-java"))
    );
    PreSpringBootstrapRunner runner = PreSpringBootstrapConfiguration.preSpringBootstrapRunner(runtimeEnvironment);

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = runner.exitCodeFor(new String[] { "list" });

      assertThat(exitCode).isZero();
      assertThat(ModuleCatalogOutput.slugsIn(outputCaptor.getStandardOutput()))
        .contains("maven-java")
        .doesNotContain("gradle-java", "gitpod");
    }
  }

  private void createStandardRuntimeConfiguration() throws IOException {
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
  }
}
