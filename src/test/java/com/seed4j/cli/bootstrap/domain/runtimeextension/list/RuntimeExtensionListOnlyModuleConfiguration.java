package com.seed4j.cli.bootstrap.domain.runtimeextension.list;

import com.seed4j.module.domain.resource.Seed4JModuleResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuntimeExtensionListOnlyModuleConfiguration {

  @Bean
  Seed4JModuleResource runtimeExtensionListOnlyModule(RuntimeExtensionListOnlyApplicationService applicationService) {
    return Seed4JModuleResource.builder()
      .slug(RuntimeExtensionListOnlyModuleSlug.RUNTIME_EXTENSION_LIST_ONLY)
      .withoutProperties()
      .apiDoc("Runtime", "Available only in extension mode")
      .standalone()
      .tags("runtime", "extension")
      .factory(applicationService::buildModule);
  }
}
