package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class RuntimeExtensionModeDisabler {

  private static final Path CONFIG_PATH = Path.of(".config", "seed4j-cli", "config.yml");
  private final Path userHome;
  private final RuntimeModeConfigReader runtimeModeConfigReader;

  public RuntimeExtensionModeDisabler(Path userHome) {
    this.userHome = userHome;
    this.runtimeModeConfigReader = new RuntimeModeConfigReader();
  }

  public Path disable() {
    Map<Object, Object> currentConfiguration = runtimeModeConfigReader.configuration(userHome);
    Path configPath = userHome.resolve(CONFIG_PATH);

    try {
      RuntimeModeConfigurationWriter.writeMode(configPath, currentConfiguration, RuntimeMode.STANDARD);
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException("Could not update ~/.config/seed4j-cli/config.yml.");
    }

    return configPath;
  }
}
