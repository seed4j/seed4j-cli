package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.BashCompletionInstaller;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class BashCompletionInstallationConfiguration {

  @Bean
  BashCompletionInstaller bashCompletionInstaller() {
    return new FileSystemBashCompletionInstaller(Path.of(System.getProperty("user.home")));
  }
}
