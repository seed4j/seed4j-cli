package com.seed4j.cli.command.domain.moduleset;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ModuleSetParameterResolver {

  List<ModuleSetParameterResolution> resolve(
    List<ModuleSetPropertyDefinition> definitions,
    ExplicitModuleSetParameters explicitParameters,
    ModuleSetHistoryParameters historyParameters
  ) {
    List<ModuleSetParameterResolution> resolutions = new ArrayList<>();
    for (ModuleSetPropertyDefinition definition : definitions) {
      resolutions.add(resolve(definition, explicitParameters, historyParameters));
    }
    return List.copyOf(resolutions);
  }

  private static ModuleSetParameterResolution resolve(
    ModuleSetPropertyDefinition definition,
    ExplicitModuleSetParameters explicitParameters,
    ModuleSetHistoryParameters historyParameters
  ) {
    return Optional.ofNullable(explicitParameters.values().get(definition.key()))
      .<ModuleSetParameterResolution>map(value -> resolved(definition, value, ModuleSetPropertySource.EXPLICIT_INPUT))
      .orElseGet(() -> resolveHistory(definition, historyParameters));
  }

  private static ModuleSetParameterResolution resolveHistory(
    ModuleSetPropertyDefinition definition,
    ModuleSetHistoryParameters historyParameters
  ) {
    return Optional.ofNullable(historyParameters.recognizedValues().get(definition.key()))
      .map(historyValue -> resolveRecognizedHistory(definition, historyValue))
      .orElseGet(() -> resolveWithoutRecognizedHistory(definition, historyParameters));
  }

  private static ModuleSetParameterResolution resolveRecognizedHistory(
    ModuleSetPropertyDefinition definition,
    ModuleSetParameterValue historyValue
  ) {
    return historyValue.type() == definition.type()
      ? resolved(definition, historyValue, ModuleSetPropertySource.PROJECT_HISTORY)
      : incompatible(definition, ModuleSetHistoryParameterValueType.from(historyValue.type()));
  }

  private static ModuleSetParameterResolution resolveWithoutRecognizedHistory(
    ModuleSetPropertyDefinition definition,
    ModuleSetHistoryParameters historyParameters
  ) {
    ModuleSetPropertyKey key = definition.key();
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
