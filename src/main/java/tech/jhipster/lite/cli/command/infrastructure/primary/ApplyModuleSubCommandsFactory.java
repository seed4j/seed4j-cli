package tech.jhipster.lite.cli.command.infrastructure.primary;

import org.springframework.stereotype.Component;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.domain.resource.JHipsterModuleResource;
import tech.jhipster.lite.project.application.ProjectsApplicationService;

@Component
class ApplyModuleSubCommandsFactory {

  private final JHipsterModulesApplicationService modules;
  private final ProjectsApplicationService projects;

  public ApplyModuleSubCommandsFactory(JHipsterModulesApplicationService modules, ProjectsApplicationService projects) {
    this.modules = modules;
    this.projects = projects;
  }

  public ApplyModuleSubCommand create(JHipsterModuleResource module) {
    return new ApplyModuleSubCommand(modules, module, projects);
  }
}
