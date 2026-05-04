package com.mycompany.seed4j.extension.runtime.list;

import com.seed4j.module.domain.resource.Seed4JModuleResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "seed4j.cli.runtime.mode", havingValue = "extension")
public class MyCompanyRuntimeExtensionListOnlyModuleConfiguration {

  @Bean
  MyCompanyRuntimeExtensionListOnlyApplicationService myCompanyRuntimeExtensionListOnlyApplicationService() {
    return new MyCompanyRuntimeExtensionListOnlyApplicationService();
  }

  @Bean
  Seed4JModuleResource myCompanyRuntimeExtensionListOnlyModule(MyCompanyRuntimeExtensionListOnlyApplicationService applicationService) {
    return Seed4JModuleResource.builder()
      .slug(MyCompanyRuntimeExtensionListOnlyModuleSlug.RUNTIME_EXTENSION_CUSTOM_PACKAGE_LIST_ONLY)
      .withoutProperties()
      .apiDoc("Runtime", "Available only in extension mode")
      .standalone()
      .tags("runtime", "extension")
      .factory(applicationService::buildModule);
  }
}
