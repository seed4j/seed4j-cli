package tech.jhipster.lite.cli.command.infrastructure.primary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;

@Configuration
class CommandContextTestConfig {

  @Bean
  @Primary
  @Scope("prototype")
  public ApplyModuleCommand forceNewInstanceOfApplyModuleCommand(JHipsterModulesApplicationService modules) {
    return new ApplyModuleCommand(modules);
  }
}
