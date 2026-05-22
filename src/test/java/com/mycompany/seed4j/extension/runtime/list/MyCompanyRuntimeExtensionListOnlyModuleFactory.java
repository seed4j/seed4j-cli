package com.mycompany.seed4j.extension.runtime.list;

import static com.seed4j.module.domain.Seed4JModule.moduleBuilder;

import com.seed4j.module.domain.Seed4JModule;
import com.seed4j.module.domain.properties.Seed4JModuleProperties;
import com.seed4j.shared.error.domain.Assert;

public final class MyCompanyRuntimeExtensionListOnlyModuleFactory {

  Seed4JModule buildModule(Seed4JModuleProperties properties) {
    Assert.notNull("properties", properties);

    return moduleBuilder(properties).build();
  }
}
