package com.mycompany.seed4j.extension.runtime.main.apply;

import com.seed4j.module.domain.Seed4JModule;
import com.seed4j.module.domain.properties.Seed4JModuleProperties;

public class RuntimeExtensionApplySharedContextApplicationService {

  private final RuntimeExtensionApplySharedContextModuleFactory moduleFactory;

  public RuntimeExtensionApplySharedContextApplicationService() {
    moduleFactory = new RuntimeExtensionApplySharedContextModuleFactory();
  }

  public Seed4JModule buildModule(Seed4JModuleProperties properties) {
    return moduleFactory.buildModule(properties);
  }
}
