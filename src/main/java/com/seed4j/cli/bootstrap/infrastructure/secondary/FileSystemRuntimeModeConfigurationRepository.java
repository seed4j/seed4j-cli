package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigReader;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class FileSystemRuntimeModeConfigurationRepository implements RuntimeModeConfigurationRepository {

  private static final Path CONFIG_PATH = Path.of(".config", "seed4j-cli", "config.yml");

  private final Path userHome;
  private final RuntimeModeConfigReader runtimeModeConfigReader;

  public FileSystemRuntimeModeConfigurationRepository(Path userHome) {
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
