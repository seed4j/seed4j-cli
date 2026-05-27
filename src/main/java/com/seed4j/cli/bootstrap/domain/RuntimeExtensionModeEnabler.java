package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;

public class RuntimeExtensionModeEnabler {

  private final Path userHome;
  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;

  public RuntimeExtensionModeEnabler(Path userHome, RuntimeModeConfigurationRepository runtimeModeConfigurationRepository) {
    this.userHome = userHome;
    this.runtimeModeConfigurationRepository = runtimeModeConfigurationRepository;
  }

  public Path enable() {
    RuntimeModeConfigurationDocument currentConfiguration = runtimeModeConfigurationRepository.readConfiguration();
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = RuntimeExtensionConfiguration.withDefaultPaths(userHome);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(RuntimeMode.EXTENSION, runtimeExtensionConfiguration);
    RuntimeSelection.resolve(runtimeConfiguration);
    Path configPath = runtimeModeConfigurationRepository.configPath();

    try {
      runtimeModeConfigurationRepository.persistMode(currentConfiguration, RuntimeMode.EXTENSION);
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException("Could not update ~/.config/seed4j-cli/config.yml.");
    }

    return configPath;
  }
}
