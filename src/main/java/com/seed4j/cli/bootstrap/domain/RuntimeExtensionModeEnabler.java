package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class RuntimeExtensionModeEnabler {

  private static final Path CONFIG_PATH = Path.of(".config", "seed4j-cli", "config.yml");
  private final Path userHome;
  private final RuntimeModeConfigReader runtimeModeConfigReader;

  public RuntimeExtensionModeEnabler(Path userHome) {
    this.userHome = userHome;
    this.runtimeModeConfigReader = new RuntimeModeConfigReader();
  }

  public Path enable() {
    Map<Object, Object> currentConfiguration = runtimeModeConfigReader.configuration(userHome);
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = RuntimeExtensionConfiguration.withDefaultPaths(userHome);
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration(RuntimeMode.EXTENSION, runtimeExtensionConfiguration);
    RuntimeSelection.resolve(runtimeConfiguration);
    Path configPath = userHome.resolve(CONFIG_PATH);

    try {
      RuntimeModeConfigurationWriter.writeMode(configPath, currentConfiguration, RuntimeMode.EXTENSION);
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException("Could not update ~/.config/seed4j-cli/config.yml.");
    }

    return configPath;
  }
}
