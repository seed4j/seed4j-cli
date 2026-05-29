package com.seed4j.cli.bootstrap.composition;

import com.seed4j.cli.bootstrap.application.RuntimeExtensionApplicationService;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuntimeExtensionApplicationConfiguration {

  @Bean
  RuntimeModeConfigurationRepository runtimeModeConfigurationRepository(@Value("${user.home}") String userHomePath) {
    return new FileSystemRuntimeModeConfigurationRepository(userHome(userHomePath));
  }

  @Bean
  RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository() {
    return new FileSystemRuntimeExtensionArtifactsRepository();
  }

  @Bean
  RuntimeExtensionApplicationService runtimeExtensionApplicationService(
    @Value("${user.home}") String userHomePath,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository,
    RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository
  ) {
    return new RuntimeExtensionApplicationService(
      userHome(userHomePath),
      runtimeModeConfigurationRepository,
      runtimeExtensionArtifactsRepository
    );
  }

  private static Path userHome(String userHomePath) {
    Assert.notBlank("userHomePath", userHomePath);
    return Path.of(userHomePath);
  }
}
