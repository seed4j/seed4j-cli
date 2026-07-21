package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.moduleset.ModuleSetCatalog;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanner;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningHistoryReader;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningRequest;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefinition;
import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModuleSetPlanningApplicationService {

  private final ModuleSetPlanner planner;

  public ModuleSetPlanningApplicationService(ModuleSetCatalog catalog, ModuleSetPlanningHistoryReader historyReader) {
    Assert.notNull("catalog", catalog);
    Assert.notNull("historyReader", historyReader);
    planner = new ModuleSetPlanner(catalog, historyReader);
  }

  public List<ModuleSetPropertyDefinition> availableProperties() {
    return planner.availableProperties();
  }

  public ModuleSetPlan plan(ModuleSetPlanningRequest request) {
    return planner.plan(request);
  }
}
