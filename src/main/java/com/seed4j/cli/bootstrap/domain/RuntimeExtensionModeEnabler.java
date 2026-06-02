package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.io.IOException;
import java.nio.file.Path;

public class RuntimeExtensionModeEnabler {

  private final RuntimeExtensionConfiguration runtimeExtensionConfiguration;
  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;

  public RuntimeExtensionModeEnabler(
    RuntimeExtensionConfiguration runtimeExtensionConfiguration,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository
  ) {
    Assert.notNull("runtimeExtensionConfiguration", runtimeExtensionConfiguration);
    Assert.notNull("runtimeModeConfigurationRepository", runtimeModeConfigurationRepository);

    this.runtimeExtensionConfiguration = runtimeExtensionConfiguration;
    this.runtimeModeConfigurationRepository = runtimeModeConfigurationRepository;
  }

  public Path enable() {
    RuntimeModeChangePlan modeChangePlan = runtimeModeConfigurationRepository.prepareModeChange(RuntimeMode.EXTENSION);
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
