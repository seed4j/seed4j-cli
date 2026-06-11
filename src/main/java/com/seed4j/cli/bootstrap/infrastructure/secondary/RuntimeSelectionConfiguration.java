package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RuntimeSelectionProperties.class)
class RuntimeSelectionConfiguration {

  @Bean
  RuntimeSelection runtimeSelection(RuntimeSelectionProperties runtimeSelectionProperties) {
    RuntimeMode runtimeMode = runtimeMode(runtimeSelectionProperties.getMode());

    if (runtimeMode == RuntimeMode.STANDARD) {
      return RuntimeSelection.standard();
    }

    return RuntimeSelection.extensionWithoutJar(
      runtimeDistributionId(runtimeSelectionProperties.getDistribution().getId()),
      runtimeDistributionVersion(runtimeSelectionProperties.getDistribution().getVersion())
    );
  }

  private static RuntimeMode runtimeMode(String runtimeMode) {
    return normalized(runtimeMode)
      .map(value -> RuntimeMode.valueOf(value.toUpperCase()))
      .orElse(RuntimeMode.STANDARD);
  }

  private static Optional<RuntimeDistributionId> runtimeDistributionId(String distributionId) {
    return normalized(distributionId).map(RuntimeDistributionId::new);
  }

  private static Optional<RuntimeDistributionVersion> runtimeDistributionVersion(String distributionVersion) {
    return normalized(distributionVersion).map(RuntimeDistributionVersion::new);
  }

  private static Optional<String> normalized(String value) {
    return Optional.ofNullable(value)
      .map(String::trim)
      .filter(text -> !text.isEmpty());
  }
}
