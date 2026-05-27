package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;

public class RuntimeExtensionModeDisabler {

  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;

  public RuntimeExtensionModeDisabler(RuntimeModeConfigurationRepository runtimeModeConfigurationRepository) {
    this.runtimeModeConfigurationRepository = runtimeModeConfigurationRepository;
  }

  public Path disable() {
    RuntimeModeConfigurationDocument currentConfiguration = runtimeModeConfigurationRepository.readConfiguration();
    Path configPath = runtimeModeConfigurationRepository.configPath();

    try {
      runtimeModeConfigurationRepository.persistMode(currentConfiguration, RuntimeMode.STANDARD);
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException("Could not update ~/.config/seed4j-cli/config.yml.");
    }

    return configPath;
  }
}
