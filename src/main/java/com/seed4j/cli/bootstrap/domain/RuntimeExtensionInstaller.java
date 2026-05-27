package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;

public class RuntimeExtensionInstaller {

  private final Path userHome;
  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;
  private final RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository;
  private final RuntimeExtensionJarLayoutValidator runtimeExtensionJarLayoutValidator;

  public RuntimeExtensionInstaller(
    Path userHome,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository
  ) {
    this(userHome, runtimeModeConfigurationRepository, runtimeExtensionArtifactsRepository, new RuntimeExtensionJarLayoutValidator());
  }

  private RuntimeExtensionInstaller(
    Path userHome,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository,
    RuntimeExtensionJarLayoutValidator runtimeExtensionJarLayoutValidator
  ) {
    this.userHome = userHome;
    this.runtimeModeConfigurationRepository = runtimeModeConfigurationRepository;
    this.runtimeExtensionArtifactsRepository = runtimeExtensionArtifactsRepository;
    this.runtimeExtensionJarLayoutValidator = runtimeExtensionJarLayoutValidator;
  }

  public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = RuntimeExtensionConfiguration.withDefaultPaths(userHome);
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
    runtimeExtensionJarLayoutValidator.validate(request.extensionJarPath());
    return runtimeModeConfigurationRepository.prepareModeChange(RuntimeMode.EXTENSION);
  }
}
