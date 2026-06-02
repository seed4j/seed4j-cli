package com.seed4j.cli.bootstrap.composition;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringBootstrapConfigurationTest {

  @Test
  void shouldBuildPrimaryRunnerFromConfigurationUsingExplicitRuntimeEnvironment() throws IOException {
    Path userHomePath = Files.createTempDirectory("seed4j-cli-");
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      new Seed4JCliHome(userHomePath),
      Path.of("seed4j-cli.jar"),
      true,
      Path.of(System.getProperty("java.home"), "bin", "java")
    );
    PreSpringBootstrapRunner preSpringBootstrapRunner = PreSpringBootstrapConfiguration.preSpringBootstrapRunner(runtimeEnvironment);

    int exitCode = preSpringBootstrapRunner.exitCodeFor(new String[] { "--version" });

    assertThat(exitCode).isZero();
  }
}
