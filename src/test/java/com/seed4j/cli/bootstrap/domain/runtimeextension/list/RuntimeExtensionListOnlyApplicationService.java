package com.seed4j.cli.bootstrap.domain.runtimeextension.list;

import com.seed4j.module.domain.Seed4JModule;
import com.seed4j.module.domain.properties.Seed4JModuleProperties;
import org.springframework.stereotype.Service;

@Service
public class RuntimeExtensionListOnlyApplicationService {

  private final RuntimeExtensionListOnlyModuleFactory moduleFactory;

  public RuntimeExtensionListOnlyApplicationService() {
    moduleFactory = new RuntimeExtensionListOnlyModuleFactory();
  }

  public Seed4JModule buildModule(Seed4JModuleProperties properties) {
    return moduleFactory.buildModule(properties);
  }
}
