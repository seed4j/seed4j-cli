package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;

public interface RuntimeModeConfigurationRepository {
  Path configPath();

  RuntimeModeConfigurationDocument readConfiguration();

  default RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
    RuntimeModeConfigurationDocument currentConfiguration = readConfiguration();
    return new RuntimeModeChangePlan() {
      @Override
      public Path configPath() {
        return RuntimeModeConfigurationRepository.this.configPath();
      }

      @Override
      public void apply() throws IOException {
        RuntimeModeConfigurationRepository.this.persistMode(currentConfiguration, targetMode);
      }
    };
  }

  RuntimeMode readMode();

  void persistMode(RuntimeModeConfigurationDocument currentConfiguration, RuntimeMode mode) throws IOException;
}
