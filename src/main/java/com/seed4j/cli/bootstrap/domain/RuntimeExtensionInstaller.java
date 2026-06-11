package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public class RuntimeExtensionInstaller {

  private final RuntimeExtensionPackageValidator runtimeExtensionPackageValidator;
  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;
  private final RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository;

  public RuntimeExtensionInstaller(
    RuntimeExtensionPackageValidator runtimeExtensionPackageValidator,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository
  ) {
    Assert.notNull("runtimeExtensionPackageValidator", runtimeExtensionPackageValidator);
    Assert.notNull("runtimeModeConfigurationRepository", runtimeModeConfigurationRepository);
    Assert.notNull("runtimeExtensionArtifactsRepository", runtimeExtensionArtifactsRepository);

    this.runtimeExtensionPackageValidator = runtimeExtensionPackageValidator;
    this.runtimeModeConfigurationRepository = runtimeModeConfigurationRepository;
    this.runtimeExtensionArtifactsRepository = runtimeExtensionArtifactsRepository;
  }

  public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
    RuntimeModeChangePlan modeChangePlan = validateInstallRequest(request);
    RuntimeExtensionReplacementStatus replacementStatus = RuntimeExtensionReplacementStatus.from(
      runtimeExtensionArtifactsRepository.activeRuntimePresent()
    );
    RuntimeExtensionArtifactsInstallation installation = runtimeExtensionArtifactsRepository.install(request);
    modeChangePlan.apply();

    return new RuntimeExtensionInstallResult(
      installation.extensionJarPath(),
      installation.metadataPath(),
      modeChangePlan.configPath(),
      replacementStatus
    );
  }

  private RuntimeModeChangePlan validateInstallRequest(RuntimeExtensionInstallRequest request) {
    runtimeExtensionPackageValidator.validate(request.extensionJarPath());
    return runtimeModeConfigurationRepository.prepareModeChange(RuntimeMode.EXTENSION);
  }
}
