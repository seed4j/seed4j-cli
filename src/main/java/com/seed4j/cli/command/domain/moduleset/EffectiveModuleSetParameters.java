package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record EffectiveModuleSetParameters(Map<ModuleSetPropertyKey, ModuleSetParameterValue> values) {
  public EffectiveModuleSetParameters {
    Assert.notNull("values", values);
    values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  public static EffectiveModuleSetParameters empty() {
    return new EffectiveModuleSetParameters(Map.of());
  }
}
