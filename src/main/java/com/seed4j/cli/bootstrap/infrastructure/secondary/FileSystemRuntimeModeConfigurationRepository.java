package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationDocument;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import java.io.IOException;
import java.nio.file.Path;

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
  public RuntimeModeConfigurationDocument readConfiguration() {
    return runtimeModeConfigReader.configuration(userHome);
  }

  @Override
  public RuntimeMode readMode() {
    return runtimeModeConfigReader.runtimeMode(userHome);
  }

  @Override
  public void persistMode(RuntimeModeConfigurationDocument currentConfiguration, RuntimeMode mode) throws IOException {
    RuntimeModeConfigurationWriter.writeMode(configPath(), currentConfiguration, mode);
  }
}
