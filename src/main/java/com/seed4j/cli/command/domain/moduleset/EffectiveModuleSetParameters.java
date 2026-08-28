package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record EffectiveModuleSetParameters(Map<ModuleSetPropertyKey, ModuleSetParameterValue> values) {
  public EffectiveModuleSetParameters {
    Assert.notNull("values", values);
    values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  public static EffectiveModuleSetParameters empty() {
    return new EffectiveModuleSetParameters(Map.of());
  }

  static EffectiveModuleSetParameters from(List<ResolvedModuleSetParameter> resolvedParameters) {
    Assert.notNull("resolvedParameters", resolvedParameters);
    Map<ModuleSetPropertyKey, ModuleSetParameterValue> values = resolvedParameters
      .stream()
      .filter(parameter -> parameter.source() != ModuleSetPropertySource.DEFAULT)
      .collect(Collectors.toMap(ResolvedModuleSetParameter::key, ResolvedModuleSetParameter::value));
    return new EffectiveModuleSetParameters(values);
  }

  boolean includes(ResolvedModuleSetParameter parameter) {
    return values.containsKey(parameter.key());
  }
}
