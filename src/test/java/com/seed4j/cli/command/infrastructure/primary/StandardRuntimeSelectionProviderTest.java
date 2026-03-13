package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.RuntimeMode;
import com.seed4j.cli.bootstrap.RuntimeSelection;
import org.junit.jupiter.api.Test;

@UnitTest
class StandardRuntimeSelectionProviderTest {

  @Test
  void shouldReadTheActiveRuntimeFromChildProcessSystemProperties() {
    String runtimeModeProperty = "seed4j.cli.runtime.mode";
    String distributionIdProperty = "seed4j.cli.runtime.distribution.id";
    String distributionVersionProperty = "seed4j.cli.runtime.distribution.version";

    String previousRuntimeMode = System.getProperty(runtimeModeProperty);
    String previousDistributionId = System.getProperty(distributionIdProperty);
    String previousDistributionVersion = System.getProperty(distributionVersionProperty);

    System.setProperty(runtimeModeProperty, "extension");
    System.setProperty(distributionIdProperty, "company-extension");
    System.setProperty(distributionVersionProperty, "1.0.0");

    try {
      RuntimeSelection runtimeSelection = new StandardRuntimeSelectionProvider().runtimeSelection();

      assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
      assertThat(runtimeSelection.distributionId()).contains("company-extension");
      assertThat(runtimeSelection.distributionVersion()).contains("1.0.0");
    } finally {
      restoreSystemProperty(runtimeModeProperty, previousRuntimeMode);
      restoreSystemProperty(distributionIdProperty, previousDistributionId);
      restoreSystemProperty(distributionVersionProperty, previousDistributionVersion);
    }
  }

  private static void restoreSystemProperty(String propertyName, String previousValue) {
    if (previousValue == null) {
      System.clearProperty(propertyName);
      return;
    }

    System.setProperty(propertyName, previousValue);
  }
}
