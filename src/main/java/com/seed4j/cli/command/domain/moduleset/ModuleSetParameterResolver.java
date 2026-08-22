package com.seed4j.cli.command.domain.moduleset;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ModuleSetParameterResolver {

  List<ModuleSetParameterResolution> resolve(
    List<ModuleSetPropertyDefinition> definitions,
    ExplicitModuleSetParameters explicitParameters,
    ModuleSetHistoryParameters historyParameters
  ) {
    List<ModuleSetParameterResolution> resolutions = new ArrayList<>();
    for (ModuleSetPropertyDefinition definition : definitions) {
      resolutions.add(resolve(definition, explicitParameters.values(), historyParameters));
    }
    return List.copyOf(resolutions);
  }

  private static ModuleSetParameterResolution resolve(
    ModuleSetPropertyDefinition definition,
    Map<ModuleSetPropertyKey, ModuleSetParameterValue> explicitParameters,
    ModuleSetHistoryParameters historyParameters
  ) {
    ModuleSetPropertyKey key = definition.key();
    if (explicitParameters.containsKey(key)) {
      return resolved(definition, explicitParameters.get(key), ModuleSetPropertySource.EXPLICIT_INPUT);
    }
    if (historyParameters.recognizedValues().containsKey(key)) {
      ModuleSetParameterValue historyValue = historyParameters.recognizedValues().get(key);
      if (historyValue.type() == definition.type()) {
        return resolved(definition, historyValue, ModuleSetPropertySource.PROJECT_HISTORY);
      }
      return incompatible(definition, ModuleSetHistoryParameterValueType.from(historyValue.type()));
    }
    if (historyParameters.containsUnsupported(key)) {
      return incompatible(definition, ModuleSetHistoryParameterValueType.UNSUPPORTED);
    }
    if (definition.mandatory()) {
      return new ModuleSetParameterResolution.RequiredMissing(new MissingRequiredModuleSetParameter(key));
    }
    return definition
      .defaultValue()
      .<ModuleSetParameterResolution>map(defaultValue -> resolved(definition, defaultValue.value(), ModuleSetPropertySource.DEFAULT))
      .orElseGet(() -> new ModuleSetParameterResolution.OptionalWithoutValue(key));
  }

  private static ModuleSetParameterResolution.Resolved resolved(
    ModuleSetPropertyDefinition definition,
    ModuleSetParameterValue value,
    ModuleSetPropertySource source
  ) {
    return new ModuleSetParameterResolution.Resolved(new ResolvedModuleSetParameter(definition.key(), value, source, definition));
  }

  private static ModuleSetParameterResolution.HistoryIncompatible incompatible(
    ModuleSetPropertyDefinition definition,
    ModuleSetHistoryParameterValueType historyType
  ) {
    return new ModuleSetParameterResolution.HistoryIncompatible(
      new ModuleSetHistoryParameterTypeMismatch(definition.key(), definition.type(), historyType)
    );
  }
}
