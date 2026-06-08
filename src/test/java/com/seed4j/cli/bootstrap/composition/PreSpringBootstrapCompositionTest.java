package com.seed4j.cli.bootstrap.composition;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.ComponentTest;
import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
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
      executablePath,
      false,
      Path.of("unused-java")
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
