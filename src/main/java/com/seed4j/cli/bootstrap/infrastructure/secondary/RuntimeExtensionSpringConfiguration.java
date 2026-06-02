package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionConfiguration;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionModeEnabler;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RuntimeExtensionSpringConfiguration {

  @Bean
  Seed4JCliHome seed4jCliHome(@Value("${user.home}") String userHomePath) {
    return Seed4JCliHome.from(userHomePath);
  }

  @Bean
  RuntimeExtensionConfiguration runtimeExtensionConfiguration(Seed4JCliHome cliHome) {
    return cliHome.runtimeExtensionConfiguration();
  }

  @Bean
  RuntimeModeConfigurationRepository runtimeModeConfigurationRepository(Seed4JCliHome cliHome) {
    return new FileSystemRuntimeModeConfigurationRepository(cliHome);
  }

  @Bean
  RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository() {
    return new FileSystemRuntimeExtensionArtifactsRepository();
  }

  @Bean
  RuntimeExtensionModeEnabler runtimeExtensionModeEnabler(
    RuntimeExtensionConfiguration runtimeExtensionConfiguration,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository
  ) {
    return new RuntimeExtensionModeEnabler(runtimeExtensionConfiguration, runtimeModeConfigurationRepository);
  }
}
