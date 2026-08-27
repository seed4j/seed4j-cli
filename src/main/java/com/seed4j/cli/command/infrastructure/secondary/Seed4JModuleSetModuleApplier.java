package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetBooleanParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetIntegerParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleApplication;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleApplier;
import com.seed4j.cli.command.domain.moduleset.ModuleSetParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetStringParameterValue;
import com.seed4j.cli.shared.error.domain.Assert;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.Seed4JModuleSlug;
import com.seed4j.module.domain.Seed4JModuleToApply;
import com.seed4j.module.domain.properties.Seed4JModuleProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class Seed4JModuleSetModuleApplier implements ModuleSetModuleApplier {

  private final Seed4JModulesApplicationService modules;

  public Seed4JModuleSetModuleApplier(Seed4JModulesApplicationService modules) {
    Assert.notNull("modules", modules);
    this.modules = modules;
  }

  @Override
  public void apply(ModuleSetModuleApplication application) {
    Seed4JModuleProperties properties = new Seed4JModuleProperties(
      application.projectPath().value().toString(),
      application.commitMode().enabled(),
      parameters(application)
    );
    modules.apply(new Seed4JModuleToApply(new Seed4JModuleSlug(application.slug().value()), properties));
  }

  private static Map<String, Object> parameters(ModuleSetModuleApplication application) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    application
      .effectiveParameters()
      .values()
      .forEach((key, value) -> parameters.put(key.value(), parameterValue(value)));
    return parameters;
  }

  private static Object parameterValue(ModuleSetParameterValue value) {
    return switch (value) {
      case ModuleSetStringParameterValue(String stringValue) -> stringValue;
      case ModuleSetIntegerParameterValue(Integer integerValue) -> integerValue;
      case ModuleSetBooleanParameterValue(Boolean booleanValue) -> booleanValue;
    };
  }
}
