package com.seed4j.cli.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeSelectionTest {

  @Test
  void shouldDefaultToStandardModeWhenRuntimeConfigurationIsMissing() {
    RuntimeSelection runtimeSelection = RuntimeSelection.resolve(null);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.STANDARD);
  }

  // [TEST] Explicit standard mode ignores missing extension jar and metadata
  // [TEST] Extension mode uses the configured jar path when provided
  // [TEST] Extension mode uses the default jar path when the configured path is absent
  // [TEST] Extension mode fails when metadata is missing
}
