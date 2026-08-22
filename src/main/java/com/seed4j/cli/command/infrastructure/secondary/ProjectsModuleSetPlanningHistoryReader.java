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
    Set<ModuleSetSlug> appliedModules = history
      .actions()
      .stream()
      .map(ProjectAction::module)
      .map(ModuleSlug::get)
      .map(ModuleSetSlug::new)
      .collect(Collectors.toUnmodifiableSet());
    Map<ModuleSetPropertyKey, ModuleSetParameterValue> parameters = new LinkedHashMap<>();
    List<UnsupportedModuleSetHistoryParameter> unsupportedParameters = new ArrayList<>();
    for (Map.Entry<String, Object> entry : history.latestProperties().get().entrySet()) {
      ModuleSetPropertyKey key = new ModuleSetPropertyKey(entry.getKey());
      switch (entry.getValue()) {
        case null -> unsupportedParameters.add(new UnsupportedModuleSetHistoryParameter(key));
        case String stringValue -> parameters.put(key, new ModuleSetStringParameterValue(stringValue));
        case Integer integerValue -> parameters.put(key, new ModuleSetIntegerParameterValue(integerValue));
        case Boolean booleanValue -> parameters.put(key, new ModuleSetBooleanParameterValue(booleanValue));
        default -> unsupportedParameters.add(new UnsupportedModuleSetHistoryParameter(key));
      }
    }

    return new ModuleSetPlanningHistory(appliedModules, new ModuleSetHistoryParameters(parameters, unsupportedParameters));
  }
}
