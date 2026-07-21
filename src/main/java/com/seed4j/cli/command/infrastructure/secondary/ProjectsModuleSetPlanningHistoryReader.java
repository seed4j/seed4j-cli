package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistory;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistoryReader;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyKey;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.shared.error.domain.Assert;
import com.seed4j.project.application.ProjectsApplicationService;
import com.seed4j.project.domain.ModuleSlug;
import com.seed4j.project.domain.ProjectPath;
import com.seed4j.project.domain.history.ProjectAction;
import com.seed4j.project.domain.history.ProjectHistory;
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
    Map<ModuleSetPropertyKey, Object> parameters = history
      .latestProperties()
      .get()
      .entrySet()
      .stream()
      .collect(Collectors.toUnmodifiableMap(entry -> new ModuleSetPropertyKey(entry.getKey()), Map.Entry::getValue));

    return new ModuleSetPlanningHistory(appliedModules, parameters);
  }
}
