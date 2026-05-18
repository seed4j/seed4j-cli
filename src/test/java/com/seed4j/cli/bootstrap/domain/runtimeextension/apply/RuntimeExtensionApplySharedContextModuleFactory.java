package com.seed4j.cli.bootstrap.domain.runtimeextension.apply;

import static com.seed4j.module.domain.Seed4JModule.from;
import static com.seed4j.module.domain.Seed4JModule.moduleBuilder;
import static com.seed4j.module.domain.Seed4JModule.packageName;
import static com.seed4j.module.domain.Seed4JModule.to;
import static com.seed4j.module.domain.nodejs.Seed4JNodePackagesVersionSource.COMMON;

import com.seed4j.module.domain.Seed4JModule;
import com.seed4j.module.domain.file.Seed4JDestination;
import com.seed4j.module.domain.file.Seed4JSource;
import com.seed4j.module.domain.properties.Seed4JModuleProperties;
import com.seed4j.shared.error.domain.Assert;

public final class RuntimeExtensionApplySharedContextModuleFactory {

  private static final Seed4JSource SOURCE = from("prettier");
  private static final Seed4JDestination DESTINATION = to(".");

  Seed4JModule buildModule(Seed4JModuleProperties properties) {
    Assert.notNull("properties", properties);

    return moduleBuilder(properties)
      .context()
      .put("endOfLine", endOfLine(properties))
      .and()
      .files()
      .batch(SOURCE, DESTINATION)
      .addTemplate(".prettierrc")
      .and()
      .and()
      .packageJson()
      .addDevDependency(packageName("prettier"), COMMON)
      .and()
      .build();
  }

  private String endOfLine(Seed4JModuleProperties properties) {
    return properties.getOrDefaultString("endOfLine", "lf");
  }
}
