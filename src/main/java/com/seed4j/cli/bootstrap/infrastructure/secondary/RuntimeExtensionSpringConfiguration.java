package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionModeEnabler;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionPackageValidator;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionSelectionRepository;
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
  RuntimeModeConfigurationRepository runtimeModeConfigurationRepository(Seed4JCliHome cliHome) {
    return new FileSystemRuntimeModeConfigurationRepository(cliHome);
  }

  @Bean
  RuntimeExtensionPackageValidator runtimeExtensionPackageValidator() {
    return new JarRuntimeExtensionPackageValidator();
  }

  @Bean
  RuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository(Seed4JCliHome cliHome) {
    return new FileSystemRuntimeExtensionArtifactsRepository(cliHome);
  }

  @Bean
  RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository(
    Seed4JCliHome cliHome,
    RuntimeExtensionPackageValidator runtimeExtensionPackageValidator
  ) {
    return new FileSystemRuntimeExtensionSelectionRepository(cliHome, runtimeExtensionPackageValidator);
  }

  @Bean
  RuntimeExtensionModeEnabler runtimeExtensionModeEnabler(
    RuntimeExtensionSelectionRepository runtimeExtensionSelectionRepository,
    RuntimeModeConfigurationRepository runtimeModeConfigurationRepository
  ) {
    return new RuntimeExtensionModeEnabler(runtimeExtensionSelectionRepository, runtimeModeConfigurationRepository);
  }
}
