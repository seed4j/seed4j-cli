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
    RuntimeModeChangePlan modeChangePlan = runtimeModeConfigurationRepository.prepareModeChange(RuntimeMode.EXTENSION);
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = RuntimeExtensionConfiguration.withDefaultPaths(userHome);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(RuntimeMode.EXTENSION, runtimeExtensionConfiguration);
    RuntimeSelection.resolve(runtimeConfiguration);

    try {
      modeChangePlan.apply();
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError("Could not update ~/.config/seed4j-cli/config.yml.", ioException);
    }

    return modeChangePlan.configPath();
  }
}
