package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.bootstrap.infrastructure.primary.JavaSeed4JCliHomeReader;
import com.seed4j.cli.command.domain.AgentSkillInstaller;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AgentSkillInstallationConfiguration {

  @Bean
  AgentSkillInstaller agentSkillInstaller(JavaSeed4JCliHomeReader cliHomeReader) {
    CurrentAgentSkillInstallationPathResolver pathResolver = new CurrentAgentSkillInstallationPathResolver(cliHomeReader, () ->
      Path.of("").toAbsolutePath().normalize()
    );
    return new FileSystemAgentSkillInstaller(pathResolver, new BaseJarAgentSkillResources(), new NioAgentSkillFileOperations());
  }
}
