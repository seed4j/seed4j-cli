package com.mycompany.seed4j.extension.runtime.fixture;

import com.mycompany.seed4j.extension.runtime.main.apply.RuntimeExtensionCommonSourceNodePackagesVersionsReader;
import com.mycompany.seed4j.extension.runtime.main.list.RuntimeExtensionListOnlyModuleConfiguration;
import com.seed4j.module.infrastructure.secondary.nodejs.NodePackagesVersionsReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@Import(RuntimeExtensionListOnlyModuleConfiguration.class)
public class RuntimeExtensionCommonSourceApplication {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public NodePackagesVersionsReader runtimeExtensionCommonSourceNodePackagesVersionsReader() {
    return new RuntimeExtensionCommonSourceNodePackagesVersionsReader();
  }
}
