package com.seed4j.cli.bootstrap.domain.runtimeextension.list;

import com.seed4j.module.domain.resource.Seed4JModuleRank;
import com.seed4j.module.domain.resource.Seed4JModuleSlugFactory;

public enum RuntimeExtensionListOnlyModuleSlug implements Seed4JModuleSlugFactory {
  RUNTIME_EXTENSION_LIST_ONLY("runtime-extension-list-only", Seed4JModuleRank.RANK_D);

  private final String value;
  private final Seed4JModuleRank rank;

  RuntimeExtensionListOnlyModuleSlug(String value, Seed4JModuleRank rank) {
    this.value = value;
    this.rank = rank;
  }

  @Override
  public String get() {
    return value;
  }

  @Override
  public Seed4JModuleRank rank() {
    return rank;
  }
}
