package com.seed4j.cli.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeSelectionTest {

  @Test
  void shouldDefaultToStandardModeWhenRuntimeConfigurationIsMissing() {
    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(null);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.STANDARD);
  }

  @Test
  void shouldIgnoreMissingExtensionArtifactsWhenModeIsStandard() {
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.STANDARD,
      new RuntimeExtensionConfiguration(Path.of("missing-extension.jar"), Path.of("missing-metadata.yml"))
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(runtimeSelection.extensionJarPath()).isEmpty();
  }

  @Test
  void shouldUseConfiguredJarPathWhenModeIsExtension() {
    Path configuredJarPath = Path.of("company-extension.jar");
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(
      RuntimeMode.EXTENSION,
      new RuntimeExtensionConfiguration(configuredJarPath, Path.of("extension-metadata.yml"))
    );

    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(runtimeConfiguration);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionJarPath()).contains(configuredJarPath);
  }

  // [TEST] Extension mode uses the default jar path when the configured path is absent
  // [TEST] Extension mode fails when metadata is missing
}
