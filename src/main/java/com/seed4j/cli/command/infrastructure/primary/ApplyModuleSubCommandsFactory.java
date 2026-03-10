package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import com.seed4j.project.application.ProjectsApplicationService;
import org.springframework.stereotype.Component;

@Component
class ApplyModuleSubCommandsFactory {

  private final Seed4JModulesApplicationService modules;
  private final ProjectsApplicationService projects;

  public ApplyModuleSubCommandsFactory(Seed4JModulesApplicationService modules, ProjectsApplicationService projects) {
    this.modules = modules;
    this.projects = projects;
  }

  public ApplyModuleSubCommand create(Seed4JModuleResource module) {
    return new ApplyModuleSubCommand(modules, module, projects);
  }
}
