package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

public class RuntimeExtensionModeDisabler {

  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;

  public RuntimeExtensionModeDisabler(RuntimeModeConfigurationRepository runtimeModeConfigurationRepository) {
    this.runtimeModeConfigurationRepository = runtimeModeConfigurationRepository;
  }

  public Path disable() {
    RuntimeModeChangePlan modeChangePlan = runtimeModeConfigurationRepository.prepareModeChange(RuntimeMode.STANDARD);
    modeChangePlan.apply();

    return modeChangePlan.configPath();
  }
}
