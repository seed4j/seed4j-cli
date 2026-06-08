package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstaller;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionPackageValidator;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Service;

@Service
public class RuntimeExtensionApplicationService {

  private final RuntimeExtensionInstaller runtimeExtensionInstaller;
  private final RuntimeSelection runtimeSelection;

  public RuntimeExtensionApplicationService(
    RuntimeExtensionPackageValidator runtimeExtensionPackageValidator,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository,
    RuntimeSelection runtimeSelection
  ) {
    Assert.notNull("runtimeExtensionPackageValidator", runtimeExtensionPackageValidator);
    Assert.notNull("runtimeModeConfigurationRepository", runtimeModeConfigurationRepository);
    Assert.notNull("runtimeExtensionArtifactsRepository", runtimeExtensionArtifactsRepository);
    Assert.notNull("runtimeSelection", runtimeSelection);

    this.runtimeExtensionInstaller = new RuntimeExtensionInstaller(
      runtimeExtensionPackageValidator,
      runtimeModeConfigurationRepository,
      runtimeExtensionArtifactsRepository
    );
    this.runtimeSelection = runtimeSelection;
  }

  public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
    Assert.notNull("request", request);
    return runtimeExtensionInstaller.install(request);
  }

  public RuntimeSelection activeRuntimeSelection() {
    return runtimeSelection;
  }
}
