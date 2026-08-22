package com.seed4j.cli.command.domain.moduleset;

import java.util.ArrayList;
import java.util.List;

record ModuleSetParameterResolutionSummary(
  List<ResolvedModuleSetParameter> resolvedParameters,
  List<MissingRequiredModuleSetParameter> missingRequiredParameters,
  List<ModuleSetHistoryParameterTypeMismatch> historyMismatches
) {
  ModuleSetParameterResolutionSummary {
    resolvedParameters = List.copyOf(resolvedParameters);
    missingRequiredParameters = List.copyOf(missingRequiredParameters);
    historyMismatches = List.copyOf(historyMismatches);
  }

  static ModuleSetParameterResolutionSummary from(List<ModuleSetParameterResolution> resolutions) {
    ModuleSetParameterResolutionSummary summary = empty();
    for (ModuleSetParameterResolution resolution : resolutions) {
      summary = summary.add(resolution);
    }
    return summary;
  }

  private static ModuleSetParameterResolutionSummary empty() {
    return new ModuleSetParameterResolutionSummary(List.of(), List.of(), List.of());
  }

  private ModuleSetParameterResolutionSummary add(ModuleSetParameterResolution resolution) {
    return switch (resolution) {
      case ModuleSetParameterResolution.Resolved resolved -> new ModuleSetParameterResolutionSummary(
        append(resolvedParameters, resolved.parameter()),
        missingRequiredParameters,
        historyMismatches
      );
      case ModuleSetParameterResolution.RequiredMissing missing -> new ModuleSetParameterResolutionSummary(
        resolvedParameters,
        append(missingRequiredParameters, missing.parameter()),
        historyMismatches
      );
      case ModuleSetParameterResolution.HistoryIncompatible incompatible -> new ModuleSetParameterResolutionSummary(
        resolvedParameters,
        missingRequiredParameters,
        append(historyMismatches, incompatible.mismatch())
      );
      case ModuleSetParameterResolution.OptionalWithoutValue _ -> this;
    };
  }

  private static <T> List<T> append(List<T> values, T value) {
    List<T> updatedValues = new ArrayList<>(values);
    updatedValues.add(value);
    return List.copyOf(updatedValues);
  }
}
