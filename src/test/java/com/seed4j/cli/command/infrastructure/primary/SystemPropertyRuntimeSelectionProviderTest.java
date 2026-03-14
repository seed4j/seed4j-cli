package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import java.util.Map;
import org.junit.jupiter.api.Test;

@UnitTest
class SystemPropertyRuntimeSelectionProviderTest {

  @Test
  void shouldReconstructTheExtensionRuntimeSelectionFromChildProcessSystemProperties() {
    RuntimeSelectionProvider runtimeSelectionProvider = new SystemPropertyRuntimeSelectionProvider(
      Map.of(
        "seed4j.cli.runtime.mode",
        "extension",
        "seed4j.cli.runtime.distribution.id",
        "company-extension",
        "seed4j.cli.runtime.distribution.version",
        "1.0.0"
      )
    );

    RuntimeSelection runtimeSelection = runtimeSelectionProvider.runtimeSelection();

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.distributionId()).contains("company-extension");
    assertThat(runtimeSelection.distributionVersion()).contains("1.0.0");
  }
}
