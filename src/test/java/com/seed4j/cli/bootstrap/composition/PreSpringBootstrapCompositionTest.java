package com.seed4j.cli.bootstrap.composition;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.application.PreSpringRuntimeEnvironmentProvider;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringLauncherAssembler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringBootstrapCompositionTest {

  @Test
  void shouldBuildPrimaryAssemblerFromCompositionUsingExplicitRuntimeProvider() throws IOException {
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
    RecordingPreSpringRuntimeEnvironmentProvider preSpringRuntimeEnvironmentProvider = new RecordingPreSpringRuntimeEnvironmentProvider(
      runtimeEnvironment
    );
    PreSpringLauncherAssembler preSpringLauncherAssembler = PreSpringBootstrapComposition.preSpringLauncherAssembler(
      preSpringRuntimeEnvironmentProvider
    );

    int exitCode = preSpringLauncherAssembler.exitCodeFor(new String[] { "--version" });

    assertThat(exitCode).isZero();
    assertThat(preSpringRuntimeEnvironmentProvider.currentCalls()).isEqualTo(1);
  }

  private static final class RecordingPreSpringRuntimeEnvironmentProvider implements PreSpringRuntimeEnvironmentProvider {

    private final PreSpringRuntimeEnvironment runtimeEnvironment;
    private int currentCalls;

    private RecordingPreSpringRuntimeEnvironmentProvider(PreSpringRuntimeEnvironment runtimeEnvironment) {
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
