package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeChangePlan;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationDocument;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationPath;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import java.io.IOException;

public class FileSystemRuntimeModeConfigurationRepository implements RuntimeModeConfigurationRepository {

  private final Seed4JCliHome cliHome;
  private final RuntimeModeConfigReader runtimeModeConfigReader;

  public FileSystemRuntimeModeConfigurationRepository(Seed4JCliHome cliHome) {
    this.cliHome = cliHome;
    this.runtimeModeConfigReader = new RuntimeModeConfigReader();
  }

  @Override
  public RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
    return new FileSystemRuntimeModeChangePlan(configPath(), currentConfiguration(), targetMode);
  }

  @Override
  public RuntimeMode readMode() {
    return runtimeModeConfigReader.runtimeMode(configPath().path());
  }

  private RuntimeModeConfigurationPath configPath() {
    return new RuntimeModeConfigurationPath(cliHome.configPath());
  }

  private RuntimeModeConfigurationDocument currentConfiguration() {
    return runtimeModeConfigReader.configuration(configPath().path());
  }

  private record FileSystemRuntimeModeChangePlan(
    RuntimeModeConfigurationPath configPath,
    RuntimeModeConfigurationDocument currentConfiguration,
    RuntimeMode targetMode
  ) implements RuntimeModeChangePlan {
    @Override
    public void apply() {
      try {
        RuntimeModeConfigurationWriter.writeMode(configPath.path(), currentConfiguration, targetMode);
      } catch (IOException ioException) {
        throw InvalidRuntimeConfigurationException.technicalError("Could not update ~/.config/seed4j-cli/config.yml.", ioException);
      }
    }
  }
}
