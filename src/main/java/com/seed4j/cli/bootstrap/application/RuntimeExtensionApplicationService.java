package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstaller;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public class RuntimeExtensionApplicationService {

  private final RuntimeExtensionInstaller runtimeExtensionInstaller;

  public RuntimeExtensionApplicationService(
    Path userHome,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository
  ) {
    this.runtimeExtensionInstaller = new RuntimeExtensionInstaller(
      userHome,
      runtimeModeConfigurationRepository,
      runtimeExtensionArtifactsRepository
    );
  }

  public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
    Assert.notNull("request", request);
    return runtimeExtensionInstaller.install(request);
  }
}
