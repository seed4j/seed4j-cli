package com.seed4j.cli.bootstrap.domain.runtimeextension.apply;

import com.seed4j.module.domain.resource.Seed4JModuleResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "seed4j.cli.runtime.mode", havingValue = "extension")
public class RuntimeExtensionApplySharedContextModuleConfiguration {

  @Bean
  RuntimeExtensionApplySharedContextApplicationService runtimeExtensionApplySharedContextApplicationService() {
    return new RuntimeExtensionApplySharedContextApplicationService();
  }

  @Bean
  Seed4JModuleResource runtimeExtensionApplySharedContextModule(RuntimeExtensionApplySharedContextApplicationService applicationService) {
    return Seed4JModuleResource.builder()
      .slug(RuntimeExtensionApplySharedContextModuleSlug.RUNTIME_EXTENSION_APPLY_SHARED_CONTEXT)
      .withoutProperties()
      .apiDoc("Runtime", "Applies extension module using shared runtime overrides")
      .standalone()
      .tags("runtime", "extension")
      .factory(applicationService::buildModule);
  }
}
