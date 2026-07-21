package com.seed4j.cli.command.domain.moduleset;

import java.util.List;

public interface ModuleSetCatalog {
  List<ModuleSetModule> modules();

  List<ModuleSetSlug> sort(List<ModuleSetSlug> requestedModules);
}
