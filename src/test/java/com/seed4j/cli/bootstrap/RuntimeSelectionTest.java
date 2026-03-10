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

  // [TEST] Extension mode uses the configured jar path when provided
  // [TEST] Extension mode uses the default jar path when the configured path is absent
  // [TEST] Extension mode fails when metadata is missing
}
