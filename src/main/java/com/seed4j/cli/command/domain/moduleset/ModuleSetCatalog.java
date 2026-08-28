package com.seed4j.cli.command.domain.moduleset;

import java.util.List;

/** Stable module metadata and ordering for the lifetime of a planner. */
public interface ModuleSetCatalog {
  /** Returns visible modules whose repeated property keys have one type across the complete catalog. */
  List<ModuleSetModule> modules();

  List<ModuleSetSlug> sort(List<ModuleSetSlug> requestedModules);
}
