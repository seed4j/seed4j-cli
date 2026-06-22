package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.module.domain.properties.Seed4JPropertyDefaultValue;
import com.seed4j.module.domain.resource.Seed4JModulePropertiesDefinition;
import com.seed4j.module.domain.resource.Seed4JModulePropertyDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class ApplyModuleParameterResolver {

  ResolvedModuleParameters resolve(
    Seed4JModulePropertiesDefinition definitions,
    Map<String, Object> explicitParameters,
    Map<String, Object> historyParameters
  ) {
    List<ResolvedModuleParameter> parameters = definitions
      .stream()
      .map(definition -> resolve(definition, explicitParameters, historyParameters))
      .flatMap(Optional::stream)
      .toList();
    List<MissingRequiredModuleParameter> missingRequiredParameters = definitions
      .stream()
      .filter(Seed4JModulePropertyDefinition::isMandatory)
      .filter(definition -> missingRequired(definition, explicitParameters, historyParameters))
      .map(this::missingRequiredParameter)
      .toList();

    return new ResolvedModuleParameters(parameters, missingRequiredParameters);
  }

  private Optional<ResolvedModuleParameter> resolve(
    Seed4JModulePropertyDefinition definition,
    Map<String, Object> explicitParameters,
    Map<String, Object> historyParameters
  ) {
    String name = definition.key().get();

    if (explicitParameters.containsKey(name)) {
      return Optional.of(parameter(definition, explicitParameters.get(name), ModuleParameterSource.EXPLICIT));
    }

    if (historyParameters.containsKey(name)) {
      return Optional.of(parameter(definition, historyParameters.get(name), ModuleParameterSource.PROJECT_HISTORY));
    }

    if (definition.isMandatory()) {
      return Optional.empty();
    }

    return definition
      .defaultValue()
      .map(Seed4JPropertyDefaultValue::get)
      .map(value -> parameter(definition, value, ModuleParameterSource.DEFAULT));
  }

  private ResolvedModuleParameter parameter(Seed4JModulePropertyDefinition definition, Object value, ModuleParameterSource source) {
    return new ResolvedModuleParameter(definition.key().get(), value, source, ApplyModuleSubCommand.toDashedFormat(definition.key()));
  }

  private MissingRequiredModuleParameter missingRequiredParameter(Seed4JModulePropertyDefinition definition) {
    return new MissingRequiredModuleParameter(definition.key().get(), ApplyModuleSubCommand.toDashedFormat(definition.key()));
  }

  private boolean missingRequired(
    Seed4JModulePropertyDefinition definition,
    Map<String, Object> explicitParameters,
    Map<String, Object> historyParameters
  ) {
    String name = definition.key().get();

    return !explicitParameters.containsKey(name) && !historyParameters.containsKey(name);
  }
}
