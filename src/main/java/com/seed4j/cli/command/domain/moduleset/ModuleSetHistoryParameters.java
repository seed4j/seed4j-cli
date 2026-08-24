package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;
import java.util.Map;

public record ModuleSetHistoryParameters(
  Map<ModuleSetPropertyKey, ModuleSetParameterValue> recognizedValues,
  List<UnsupportedModuleSetHistoryParameter> unsupportedValues
) {
  public ModuleSetHistoryParameters {
    Assert.notNull("recognizedValues", recognizedValues);
    Assert.notNull("unsupportedValues", unsupportedValues);
    recognizedValues = Map.copyOf(recognizedValues);
    unsupportedValues = List.copyOf(unsupportedValues);
  }

  public boolean containsUnsupported(ModuleSetPropertyKey key) {
    return unsupportedValues.stream().anyMatch(parameter -> parameter.key().equals(key));
  }
}
