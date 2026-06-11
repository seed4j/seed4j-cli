package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@UnitTest
class RuntimeSelectionConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(
    RuntimeSelectionConfiguration.class
  );

  @Test
  void shouldExposeStandardRuntimeSelectionWhenNoRuntimePropertiesAreDefined() {
    contextRunner.run(context -> {
      RuntimeSelection runtimeSelection = context.getBean(RuntimeSelection.class);

      assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.STANDARD);
      assertThat(runtimeSelection.distributionId()).isEmpty();
      assertThat(runtimeSelection.distributionVersion()).isEmpty();
    });
  }

  @Test
  void shouldBindRuntimeSelectionFromExtensionRuntimeProperties() {
    contextRunner
      .withPropertyValues(
        "seed4j.cli.runtime.mode=extension",
        "seed4j.cli.runtime.distribution.id=company-extension",
        "seed4j.cli.runtime.distribution.version=1.0.0"
      )
      .run(context -> {
        RuntimeSelection runtimeSelection = context.getBean(RuntimeSelection.class);

        assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
        assertThat(runtimeSelection.distributionId()).contains(new RuntimeDistributionId("company-extension"));
        assertThat(runtimeSelection.distributionVersion()).contains(new RuntimeDistributionVersion("1.0.0"));
      });
  }

  @Test
  void shouldTreatBlankDistributionPropertiesAsAbsent() {
    contextRunner
      .withPropertyValues(
        "seed4j.cli.runtime.mode=extension",
        "seed4j.cli.runtime.distribution.id=   ",
        "seed4j.cli.runtime.distribution.version=\t"
      )
      .run(context -> {
        RuntimeSelection runtimeSelection = context.getBean(RuntimeSelection.class);

        assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
        assertThat(runtimeSelection.distributionId()).isEmpty();
        assertThat(runtimeSelection.distributionVersion()).isEmpty();
      });
  }
}
