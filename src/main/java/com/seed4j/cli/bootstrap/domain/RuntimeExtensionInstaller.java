package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class RuntimeExtensionInstaller {

  private final Path userHome;
  private final RuntimeModeConfigurationRepository runtimeModeConfigurationRepository;
  private final RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository;
  private final RuntimeExtensionJarLayoutValidator runtimeExtensionJarLayoutValidator;

  public RuntimeExtensionInstaller(Path userHome) {
    this(
      userHome,
      new RuntimeModeConfigurationFileSystemRepository(userHome),
      new RuntimeExtensionArtifactsFileSystemRepository(),
      new RuntimeExtensionJarLayoutValidator()
    );
  }

  public RuntimeExtensionInstaller(
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
    Map<Object, Object> currentConfiguration = validateInstallRequest(request);
    boolean runtimeReplaced = runtimeExtensionArtifactsRepository.activeRuntimePresent(runtimeExtensionConfiguration);

    try {
      runtimeExtensionArtifactsRepository.install(request, runtimeExtensionConfiguration);
      runtimeModeConfigurationRepository.persistExtensionMode(currentConfiguration);
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException("Could not install runtime extension: " + ioException.getMessage());
    }

    return new RuntimeExtensionInstallResult(
      runtimeExtensionConfiguration.jarPath(),
      runtimeExtensionConfiguration.metadataPath(),
      runtimeModeConfigurationRepository.configPath(),
      runtimeReplaced
    );
  }

  private Map<Object, Object> validateInstallRequest(RuntimeExtensionInstallRequest request) {
    runtimeExtensionJarLayoutValidator.validate(request.extensionJarPath());
    return runtimeModeConfigurationRepository.readCurrentConfiguration();
  }
}
