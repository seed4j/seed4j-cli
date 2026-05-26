package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

final class RuntimeModeConfigurationFileSystemRepository implements RuntimeModeConfigurationRepository {

  private static final Path CONFIG_PATH = Path.of(".config", "seed4j-cli", "config.yml");

  private final Path userHome;
  private final RuntimeModeConfigReader runtimeModeConfigReader;

  RuntimeModeConfigurationFileSystemRepository(Path userHome) {
    this.userHome = userHome;
    this.runtimeModeConfigReader = new RuntimeModeConfigReader();
  }

  @Override
  public Path configPath() {
    return userHome.resolve(CONFIG_PATH);
  }

  @Override
  public Map<Object, Object> readCurrentConfiguration() {
    return runtimeModeConfigReader.configuration(userHome);
  }

  @Override
  public void persistExtensionMode(Map<Object, Object> currentConfiguration) throws IOException {
    RuntimeModeConfigurationWriter.writeMode(configPath(), currentConfiguration, RuntimeMode.EXTENSION);
  }
}
