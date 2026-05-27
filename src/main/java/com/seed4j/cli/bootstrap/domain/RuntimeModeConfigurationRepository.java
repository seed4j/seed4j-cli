package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;

public interface RuntimeModeConfigurationRepository {
  Path configPath();

  RuntimeModeConfigurationDocument readConfiguration();

  RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode);

  RuntimeMode readMode();

  void persistMode(RuntimeModeConfigurationDocument currentConfiguration, RuntimeMode mode) throws IOException;
}
