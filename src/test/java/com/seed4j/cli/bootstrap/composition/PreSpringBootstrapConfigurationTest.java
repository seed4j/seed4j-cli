package com.seed4j.cli.bootstrap.composition;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.PreSpringRuntimeEnvironmentReader;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringBootstrapConfigurationTest {

  @Test
  void shouldBuildPrimaryRunnerFromConfigurationUsingExplicitRuntimeReader() throws IOException {
    Path userHomePath = Files.createTempDirectory("seed4j-cli-");
    Path configPath = userHomePath.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
      """
    );
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      userHomePath,
      Path.of("seed4j-cli.jar"),
      "2.2.0",
      true,
      Path.of(System.getProperty("java.home"), "bin", "java")
    );
    RecordingPreSpringRuntimeEnvironmentReader preSpringRuntimeEnvironmentReader = new RecordingPreSpringRuntimeEnvironmentReader(
      runtimeEnvironment
    );
    PreSpringBootstrapRunner preSpringBootstrapRunner = PreSpringBootstrapConfiguration.preSpringBootstrapRunner(
      preSpringRuntimeEnvironmentReader
    );

    int exitCode = preSpringBootstrapRunner.exitCodeFor(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(preSpringRuntimeEnvironmentReader.currentCalls()).isEqualTo(1);
  }

  private static final class RecordingPreSpringRuntimeEnvironmentReader implements PreSpringRuntimeEnvironmentReader {

    private final PreSpringRuntimeEnvironment runtimeEnvironment;
    private int currentCalls;

    private RecordingPreSpringRuntimeEnvironmentReader(PreSpringRuntimeEnvironment runtimeEnvironment) {
      this.runtimeEnvironment = runtimeEnvironment;
    }

    @Override
    public PreSpringRuntimeEnvironment current() {
      currentCalls++;
      return runtimeEnvironment;
    }

    private int currentCalls() {
      return currentCalls;
    }
  }
}
