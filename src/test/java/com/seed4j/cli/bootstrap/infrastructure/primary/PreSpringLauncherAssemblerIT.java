package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringLauncherAssemblerIT {

  @Test
  void shouldLaunchTheChildProcessPathWhenRunningInStandardModeFromAPackagedJar() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-pre-spring-assembler-child-process-");
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
    Path packagedCliJar = packagedCliJar();
    PreSpringLauncherAssembler assembler = new PreSpringLauncherAssembler();
    PreSpringBootstrapEntryPoint entryPoint = assembler.assemble(userHome, packagedCliJar, "2.2.0", false);

    int exitCode = entryPoint.launch(new String[] { "--version" });

    assertThat(exitCode).isZero();
  }

  private static Path packagedCliJar() throws IOException {
    Path targetDirectory = Path.of("target");
    List<Path> candidateJars;

    try (Stream<Path> targetFiles = Files.list(targetDirectory)) {
      candidateJars = targetFiles
        .filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().startsWith("seed4j-cli-"))
        .filter(path -> path.getFileName().toString().endsWith(".jar"))
        .filter(path -> !path.getFileName().toString().endsWith(".jar.original"))
        .sorted()
        .toList();
    }

    if (candidateJars.size() != 1) {
      throw new IllegalStateException("Expected exactly one packaged seed4j-cli jar in target/, found: " + candidateJars);
    }

    return candidateJars.getFirst();
  }
}
