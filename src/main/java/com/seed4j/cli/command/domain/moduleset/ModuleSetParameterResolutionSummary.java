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
      case ModuleSetParameterResolution.Resolved(ResolvedModuleSetParameter parameter) -> new ModuleSetParameterResolutionSummary(
        append(resolvedParameters, parameter),
        missingRequiredParameters,
        historyMismatches
      );
      case ModuleSetParameterResolution.RequiredMissing(
        MissingRequiredModuleSetParameter parameter
      ) -> new ModuleSetParameterResolutionSummary(resolvedParameters, append(missingRequiredParameters, parameter), historyMismatches);
      case ModuleSetParameterResolution.HistoryIncompatible(
        ModuleSetHistoryParameterTypeMismatch mismatch
      ) -> new ModuleSetParameterResolutionSummary(resolvedParameters, missingRequiredParameters, append(historyMismatches, mismatch));
      case ModuleSetParameterResolution.OptionalWithoutValue(_) -> this;
    };
  }

  private static <T> List<T> append(List<T> values, T value) {
    List<T> updatedValues = new ArrayList<>(values);
    updatedValues.add(value);
    return List.copyOf(updatedValues);
  }
}
