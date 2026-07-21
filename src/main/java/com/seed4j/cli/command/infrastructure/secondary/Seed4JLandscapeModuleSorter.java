package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.module.domain.Seed4JModuleSlug;
import java.util.Collection;

@FunctionalInterface
public interface Seed4JLandscapeModuleSorter {
  Collection<Seed4JModuleSlug> sort(Collection<Seed4JModuleSlug> modules);
}
