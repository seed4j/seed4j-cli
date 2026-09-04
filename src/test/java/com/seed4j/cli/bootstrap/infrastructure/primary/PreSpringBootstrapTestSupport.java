package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.SystemOutputCaptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class PreSpringBootstrapTestSupport {

  static final String RUNTIME_MODE_PROPERTY = "seed4j.cli.runtime.mode";
  static final String DISTRIBUTION_ID_PROPERTY = "seed4j.cli.runtime.distribution.id";
  static final String DISTRIBUTION_VERSION_PROPERTY = "seed4j.cli.runtime.distribution.version";
  static final String LOADER_PATH_PROPERTY = "loader.path";
  private static final String BASELINE_RUNTIME_MODE = "baseline-mode";

  private PreSpringBootstrapTestSupport() {}

  static CliLaunchResult launchCapturingOutput(PreSpringBootstrapRunner runner, String... arguments) {
    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = runner.exitCodeFor(arguments);
      return new CliLaunchResult(exitCode, outputCaptor.getOutput());
    }
  }

  static ScopedRuntimeProperties baselineRuntimeProperties() {
    ScopedRuntimeProperties runtimeProperties = ScopedRuntimeProperties.capture(
      Set.of(RUNTIME_MODE_PROPERTY, DISTRIBUTION_ID_PROPERTY, DISTRIBUTION_VERSION_PROPERTY, LOADER_PATH_PROPERTY)
    );
    System.setProperty(RUNTIME_MODE_PROPERTY, BASELINE_RUNTIME_MODE);
    System.clearProperty(DISTRIBUTION_ID_PROPERTY);
    System.clearProperty(DISTRIBUTION_VERSION_PROPERTY);
    System.clearProperty(LOADER_PATH_PROPERTY);
    return runtimeProperties;
  }

  static void assertBaselineRuntimePropertiesRestored() {
    assertThat(System.getProperty(RUNTIME_MODE_PROPERTY)).isEqualTo(BASELINE_RUNTIME_MODE);
    assertThat(System.getProperty(DISTRIBUTION_ID_PROPERTY)).isNull();
    assertThat(System.getProperty(DISTRIBUTION_VERSION_PROPERTY)).isNull();
    assertThat(System.getProperty(LOADER_PATH_PROPERTY)).isNull();
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

  static final class ScopedRuntimeProperties implements AutoCloseable {

    private final Map<String, Optional<String>> originalValues;

    private ScopedRuntimeProperties(Map<String, Optional<String>> originalValues) {
      this.originalValues = originalValues;
    }

    static ScopedRuntimeProperties capture(Set<String> propertyKeys) {
      Map<String, Optional<String>> capturedValues = new LinkedHashMap<>();
      for (String propertyKey : propertyKeys) {
        capturedValues.put(propertyKey, Optional.ofNullable(System.getProperty(propertyKey)));
      }
      return new ScopedRuntimeProperties(Map.copyOf(capturedValues));
    }

    @Override
    public void close() {
      for (Map.Entry<String, Optional<String>> originalValue : originalValues.entrySet()) {
        restore(originalValue);
      }
    }

    private void restore(Map.Entry<String, Optional<String>> originalValue) {
      if (originalValue.getValue().isPresent()) {
        System.setProperty(originalValue.getKey(), originalValue.getValue().orElseThrow());
      } else {
        System.clearProperty(originalValue.getKey());
      }
    }
  }
}
