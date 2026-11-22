package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetBooleanParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetHistoryParameters;
import com.seed4j.cli.command.domain.moduleset.ModuleSetIntegerParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistory;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistoryReader;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyKey;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.ModuleSetStringParameterValue;
import com.seed4j.cli.command.domain.moduleset.UnsupportedModuleSetHistoryParameter;
import com.seed4j.cli.shared.error.domain.Assert;
import com.seed4j.history.infrastructure.primary.JavaProjectHistories;
import com.seed4j.history.infrastructure.primary.ProjectHistorySummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EngineModuleSetPlanningHistoryReader implements ModuleSetPlanningHistoryReader {

  private final JavaProjectHistories projects;

  public EngineModuleSetPlanningHistoryReader(JavaProjectHistories projects) {
    Assert.notNull("projects", projects);
    this.projects = projects;
  }

  @Override
  public ModuleSetPlanningHistory history(ModuleSetProjectPath projectPath) {
    ProjectHistorySummary history = projects.get(projectPath.value().toString());
    return new ModuleSetPlanningHistory(appliedModules(history), historyParameters(history));
  }

  private static Set<ModuleSetSlug> appliedModules(ProjectHistorySummary history) {
    return history.modules().stream().map(ModuleSetSlug::new).collect(Collectors.toUnmodifiableSet());
  }

  private static ModuleSetHistoryParameters historyParameters(ProjectHistorySummary history) {
    Map<ModuleSetPropertyKey, ModuleSetParameterValue> recognizedParameters = new LinkedHashMap<>();
    List<UnsupportedModuleSetHistoryParameter> unsupportedParameters = new ArrayList<>();
    for (Map.Entry<String, Object> entry : history.properties().entrySet()) {
      switch (historyParameter(entry)) {
        case RecognizedHistoryParameter(ModuleSetPropertyKey key, ModuleSetParameterValue value) -> recognizedParameters.put(key, value);
        case UnsupportedHistoryParameter(UnsupportedModuleSetHistoryParameter parameter) -> unsupportedParameters.add(parameter);
      }
    }
    return new ModuleSetHistoryParameters(recognizedParameters, unsupportedParameters);
  }

  private static HistoryParameterFact historyParameter(Map.Entry<String, Object> entry) {
    ModuleSetPropertyKey key = new ModuleSetPropertyKey(entry.getKey());
    return switch (entry.getValue()) {
      case null -> new UnsupportedHistoryParameter(new UnsupportedModuleSetHistoryParameter(key));
      case String stringValue -> new RecognizedHistoryParameter(key, new ModuleSetStringParameterValue(stringValue));
      case Integer integerValue -> new RecognizedHistoryParameter(key, new ModuleSetIntegerParameterValue(integerValue));
      case Boolean booleanValue -> new RecognizedHistoryParameter(key, new ModuleSetBooleanParameterValue(booleanValue));
      default -> new UnsupportedHistoryParameter(new UnsupportedModuleSetHistoryParameter(key));
    };
  }

  private sealed interface HistoryParameterFact permits RecognizedHistoryParameter, UnsupportedHistoryParameter {}

  private record RecognizedHistoryParameter(ModuleSetPropertyKey key, ModuleSetParameterValue value) implements HistoryParameterFact {}

  private record UnsupportedHistoryParameter(UnsupportedModuleSetHistoryParameter parameter) implements HistoryParameterFact {}
}
