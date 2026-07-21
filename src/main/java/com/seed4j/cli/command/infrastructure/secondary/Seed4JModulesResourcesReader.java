package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.module.domain.resource.Seed4JModulesResources;

@FunctionalInterface
public interface Seed4JModulesResourcesReader {
  Seed4JModulesResources resources();
}
