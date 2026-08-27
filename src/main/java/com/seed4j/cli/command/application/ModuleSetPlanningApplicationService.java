package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.moduleset.ModuleSetCatalog;
import com.seed4j.cli.command.domain.moduleset.ModuleSetGitStateReader;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanner;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistoryReader;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningRequest;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPathValidator;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefinition;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModuleSetPlanningApplicationService {

  private final ModuleSetPlanner planner;

  public ModuleSetPlanningApplicationService(
    ModuleSetCatalog catalog,
    ModuleSetPlanningHistoryReader historyReader,
    ModuleSetProjectPathValidator projectPathValidator,
    ModuleSetGitStateReader gitStateReader
  ) {
    planner = new ModuleSetPlanner(catalog, historyReader, projectPathValidator, gitStateReader);
  }

  public List<ModuleSetPropertyDefinition> availableProperties() {
    return planner.availableProperties();
  }

  public ModuleSetPlan plan(ModuleSetPlanningRequest request) {
    return planner.plan(request);
  }
}
