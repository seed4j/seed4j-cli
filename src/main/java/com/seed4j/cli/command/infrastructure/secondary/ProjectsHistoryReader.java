package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.project.domain.ProjectPath;
import com.seed4j.project.domain.history.ProjectHistory;

@FunctionalInterface
public interface ProjectsHistoryReader {
  ProjectHistory history(ProjectPath projectPath);
}
