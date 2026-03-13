package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.bootstrap.RuntimeMode;
import com.seed4j.cli.bootstrap.RuntimeSelection;
import java.util.Map;
import java.util.Optional;

class SystemPropertyRuntimeSelectionProvider implements RuntimeSelectionProvider {

  private static final String RUNTIME_MODE_PROPERTY = "seed4j.cli.runtime.mode";
  private static final String DISTRIBUTION_ID_PROPERTY = "seed4j.cli.runtime.distribution.id";
  private static final String DISTRIBUTION_VERSION_PROPERTY = "seed4j.cli.runtime.distribution.version";

  private final Map<String, String> systemProperties;

  SystemPropertyRuntimeSelectionProvider(Map<String, String> systemProperties) {
    this.systemProperties = systemProperties;
  }

  @Override
  public RuntimeSelection runtimeSelection() {
    RuntimeMode runtimeMode = RuntimeMode.valueOf(systemProperties.getOrDefault(RUNTIME_MODE_PROPERTY, "standard").toUpperCase());

    return new RuntimeSelection(
      runtimeMode,
      Optional.empty(),
      Optional.ofNullable(systemProperties.get(DISTRIBUTION_ID_PROPERTY)),
      Optional.ofNullable(systemProperties.get(DISTRIBUTION_VERSION_PROPERTY))
    );
  }
}
