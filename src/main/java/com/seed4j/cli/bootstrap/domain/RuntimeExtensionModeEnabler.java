package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public class RuntimeExtensionModeEnabler {

  private final RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository;
  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;

  public RuntimeExtensionModeEnabler(
    RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository
  ) {
    Assert.notNull("runtimeExtensionSelectionRepository", runtimeExtensionSelectionRepository);
    Assert.notNull("runtimeModeConfigurationRepository", runtimeModeConfigurationRepository);

    this.runtimeExtensionSelectionRepository = runtimeExtensionSelectionRepository;
    this.runtimeModeConfigurationRepository = runtimeModeConfigurationRepository;
  }

  public Path enable() {
    RuntimeModeChangePlan modeChangePlan = runtimeModeConfigurationRepository.prepareModeChange(RuntimeMode.EXTENSION);
    runtimeExtensionSelectionRepository.activeRuntimeSelection();
    modeChangePlan.apply();

    return modeChangePlan.configPath().path();
  }
}
