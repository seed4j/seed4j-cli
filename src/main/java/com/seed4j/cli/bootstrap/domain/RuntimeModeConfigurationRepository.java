package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface RuntimeModeConfigurationRepository {
  Path configPath();

  Map<Object, Object> readCurrentConfiguration();

  void persistExtensionMode(Map<Object, Object> currentConfiguration) throws IOException;
}
