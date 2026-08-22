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
import com.seed4j.project.application.ProjectsApplicationService;
import com.seed4j.project.domain.ModuleSlug;
import com.seed4j.project.domain.ProjectPath;
import com.seed4j.project.domain.history.ProjectAction;
import com.seed4j.project.domain.history.ProjectHistory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProjectsModuleSetPlanningHistoryReader implements ModuleSetPlanningHistoryReader {

  private final ProjectsApplicationService projects;

  public ProjectsModuleSetPlanningHistoryReader(ProjectsApplicationService projects) {
    Assert.notNull("projects", projects);
    this.projects = projects;
  }

  @Override
  public ModuleSetPlanningHistory history(ModuleSetProjectPath projectPath) {
    ProjectHistory history = projects.getHistory(new ProjectPath(projectPath.value().toString()));
    return new ModuleSetPlanningHistory(appliedModules(history), historyParameters(history));
  }

  private static Set<ModuleSetSlug> appliedModules(ProjectHistory history) {
    return history
      .actions()
      .stream()
      .map(ProjectAction::module)
      .map(ModuleSlug::get)
      .map(ModuleSetSlug::new)
      .collect(Collectors.toUnmodifiableSet());
  }

  private static ModuleSetHistoryParameters historyParameters(ProjectHistory history) {
    Map<ModuleSetPropertyKey, ModuleSetParameterValue> recognizedParameters = new LinkedHashMap<>();
    List<UnsupportedModuleSetHistoryParameter> unsupportedParameters = new ArrayList<>();
    for (Map.Entry<String, Object> entry : history.latestProperties().get().entrySet()) {
      switch (historyParameter(entry)) {
        case RecognizedHistoryParameter recognized -> recognizedParameters.put(recognized.key(), recognized.value());
        case UnsupportedHistoryParameter unsupported -> unsupportedParameters.add(unsupported.parameter());
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
