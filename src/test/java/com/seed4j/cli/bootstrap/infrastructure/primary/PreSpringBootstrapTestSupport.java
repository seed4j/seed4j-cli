package com.seed4j.cli.bootstrap.infrastructure.primary;

import com.seed4j.cli.SystemOutputCaptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class PreSpringBootstrapTestSupport {

  private PreSpringBootstrapTestSupport() {}

  static CliLaunchResult launchCapturingOutput(PreSpringBootstrapRunner runner, String... arguments) {
    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = runner.exitCodeFor(arguments);
      return new CliLaunchResult(exitCode, outputCaptor.getOutput());
    }
  }

  static void writeRuntimeConfiguration(Path userHome, String content) throws IOException {
    writeConfiguration(runtimeConfigurationPath(userHome), content);
  }

  static void writeConfiguration(Path configPath, String content) throws IOException {
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, content);
  }

  static Path runtimeConfigurationPath(Path userHome) {
    return userHome.resolve(".config/seed4j-cli/config.yml");
  }

  static Path javaExecutablePath() {
    return Path.of(System.getProperty("java.home"), "bin", "java");
  }

  record CliLaunchResult(int exitCode, String output) {}
}
