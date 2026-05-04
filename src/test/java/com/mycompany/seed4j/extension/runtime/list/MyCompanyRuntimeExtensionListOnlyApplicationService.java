package com.mycompany.seed4j.extension.runtime.list;

import com.seed4j.module.domain.Seed4JModule;
import com.seed4j.module.domain.properties.Seed4JModuleProperties;

public class MyCompanyRuntimeExtensionListOnlyApplicationService {

  private final MyCompanyRuntimeExtensionListOnlyModuleFactory moduleFactory;

  public MyCompanyRuntimeExtensionListOnlyApplicationService() {
    moduleFactory = new MyCompanyRuntimeExtensionListOnlyModuleFactory();
  }

  public Seed4JModule buildModule(Seed4JModuleProperties properties) {
    return moduleFactory.buildModule(properties);
  }
}
