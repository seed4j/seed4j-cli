package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionConfiguration;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstaller;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionModeEnabler;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RuntimeExtensionSpringConfiguration {

  @Bean
  RuntimeExtensionConfiguration runtimeExtensionConfiguration(@Value("${user.home}") String userHomePath) {
    return RuntimeExtensionConfiguration.withDefaultPaths(userHome(userHomePath));
  }

  @Bean
  RuntimeModeConfigurationRepository runtimeModeConfigurationRepository(@Value("${user.home}") String userHomePath) {
    return new FileSystemRuntimeModeConfigurationRepository(userHome(userHomePath));
  }

  @Bean
  RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository() {
    return new FileSystemRuntimeExtensionArtifactsRepository();
  }

  @Bean
  RuntimeExtensionInstaller runtimeExtensionInstaller(
    RuntimeExtensionConfiguration runtimeExtensionConfiguration,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository
  ) {
    return new RuntimeExtensionInstaller(
      runtimeExtensionConfiguration,
      runtimeModeConfigurationRepository,
      runtimeExtensionArtifactsRepository
    );
  }

  @Bean
  RuntimeExtensionModeEnabler runtimeExtensionModeEnabler(
    RuntimeExtensionConfiguration runtimeExtensionConfiguration,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository
  ) {
    return new RuntimeExtensionModeEnabler(runtimeExtensionConfiguration, runtimeModeConfigurationRepository);
  }

  private static Path userHome(String userHomePath) {
    Assert.notBlank("userHomePath", userHomePath);
    return Path.of(userHomePath);
  }
}
