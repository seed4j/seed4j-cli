package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Map;

public record ExplicitModuleSetParameters(Map<ModuleSetPropertyKey, ModuleSetParameterValue> values) {
  public ExplicitModuleSetParameters {
    Assert.notNull("values", values);
    values = Map.copyOf(values);
  }

  public static ExplicitModuleSetParameters empty() {
    return new ExplicitModuleSetParameters(Map.of());
  }
}
