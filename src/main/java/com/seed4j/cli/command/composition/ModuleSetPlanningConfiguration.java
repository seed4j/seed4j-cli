package com.seed4j.cli.command.composition;

import com.seed4j.cli.command.domain.moduleset.ModuleSetCatalog;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistoryReader;
import com.seed4j.cli.command.infrastructure.secondary.ProjectsModuleSetPlanningHistoryReader;
import com.seed4j.cli.command.infrastructure.secondary.Seed4JModuleSetCatalog;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.project.application.ProjectsApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModuleSetPlanningConfiguration {

  @Bean
  ModuleSetCatalog moduleSetCatalog(Seed4JModulesApplicationService modules) {
    return new Seed4JModuleSetCatalog(modules::resources, slugs -> modules.landscape().sort(slugs));
  }

  @Bean
  ModuleSetPlanningHistoryReader moduleSetPlanningHistoryReader(ProjectsApplicationService projects) {
    return new ProjectsModuleSetPlanningHistoryReader(projects::getHistory);
  }
}
