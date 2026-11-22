package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.distribution.ReleaseChannel;
import com.seed4j.history.infrastructure.primary.JavaProjectHistories;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import org.springframework.stereotype.Component;

@Component
class ApplyModuleSubCommandsFactory {

  private final Seed4JModulesApplicationService modules;
  private final JavaProjectHistories projects;

  public ApplyModuleSubCommandsFactory(Seed4JModulesApplicationService modules, JavaProjectHistories projects) {
    this.modules = modules;
    this.projects = projects;
  }

  public ApplyModuleSubCommand create(Seed4JModuleResource module) {
    return new ApplyModuleSubCommand(modules, module, projects);
  }

  public UnavailableApplyModuleSubCommand createUnavailable(Seed4JModuleResource module, ReleaseChannel channel) {
    return new UnavailableApplyModuleSubCommand(module, channel);
  }
}
