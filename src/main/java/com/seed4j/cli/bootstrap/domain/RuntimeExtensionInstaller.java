package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.io.IOException;

public class RuntimeExtensionInstaller {

  private final RuntimeExtensionConfiguration runtimeExtensionConfiguration;
  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;
  private final RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository;
  private final RuntimeExtensionJarLayoutValidator runtimeExtensionJarLayoutValidator;

  public RuntimeExtensionInstaller(
    RuntimeExtensionConfiguration runtimeExtensionConfiguration,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository
  ) {
    this(
      runtimeExtensionConfiguration,
      runtimeModeConfigurationRepository,
      runtimeExtensionArtifactsRepository,
      new RuntimeExtensionJarLayoutValidator()
    );
  }

  private RuntimeExtensionInstaller(
    RuntimeExtensionConfiguration runtimeExtensionConfiguration,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository,
    RuntimeExtensionJarLayoutValidator runtimeExtensionJarLayoutValidator
  ) {
    Assert.notNull("runtimeExtensionConfiguration", runtimeExtensionConfiguration);
    Assert.notNull("runtimeModeConfigurationRepository", runtimeModeConfigurationRepository);
    Assert.notNull("runtimeExtensionArtifactsRepository", runtimeExtensionArtifactsRepository);
    Assert.notNull("runtimeExtensionJarLayoutValidator", runtimeExtensionJarLayoutValidator);

    this.runtimeExtensionConfiguration = runtimeExtensionConfiguration;
    this.runtimeModeConfigurationRepository = runtimeModeConfigurationRepository;
    this.runtimeExtensionArtifactsRepository = runtimeExtensionArtifactsRepository;
    this.runtimeExtensionJarLayoutValidator = runtimeExtensionJarLayoutValidator;
  }

  public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
    RuntimeModeChangePlan modeChangePlan = validateInstallRequest(request);
    boolean runtimeReplaced = runtimeExtensionArtifactsRepository.activeRuntimePresent(runtimeExtensionConfiguration);

    try {
      runtimeExtensionArtifactsRepository.install(request, runtimeExtensionConfiguration);
      modeChangePlan.apply();
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError("Could not install runtime extension.", ioException);
    }

    return new RuntimeExtensionInstallResult(
      runtimeExtensionConfiguration.jarPath(),
      runtimeExtensionConfiguration.metadataPath(),
      modeChangePlan.configPath(),
      runtimeReplaced
    );
  }

  private RuntimeModeChangePlan validateInstallRequest(RuntimeExtensionInstallRequest request) {
    runtimeExtensionJarLayoutValidator.validate(request.extensionJarPath().path());
    return runtimeModeConfigurationRepository.prepareModeChange(RuntimeMode.EXTENSION);
  }
}
