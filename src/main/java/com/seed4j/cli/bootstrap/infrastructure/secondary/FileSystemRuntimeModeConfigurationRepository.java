package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeChangePlan;
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
  public RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
    return new FileSystemRuntimeModeChangePlan(configPath(), currentConfiguration(), targetMode);
  }

  @Override
  public RuntimeMode readMode() {
    return runtimeModeConfigReader.runtimeMode(userHome);
  }

  private Path configPath() {
    return userHome.resolve(CONFIG_PATH);
  }

  private RuntimeModeConfigurationDocument currentConfiguration() {
    return runtimeModeConfigReader.configuration(userHome);
  }

  private record FileSystemRuntimeModeChangePlan(
    Path configPath,
    RuntimeModeConfigurationDocument currentConfiguration,
    RuntimeMode targetMode
  ) implements RuntimeModeChangePlan {
    @Override
    public void apply() throws IOException {
      RuntimeModeConfigurationWriter.writeMode(configPath, currentConfiguration, targetMode);
    }
  }
}
