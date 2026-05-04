package com.mycompany.seed4j.extension.runtime.list;

import com.seed4j.module.domain.resource.Seed4JModuleRank;
import com.seed4j.module.domain.resource.Seed4JModuleSlugFactory;

public enum MyCompanyRuntimeExtensionListOnlyModuleSlug implements Seed4JModuleSlugFactory {
  RUNTIME_EXTENSION_CUSTOM_PACKAGE_LIST_ONLY("runtime-extension-custom-package-list-only", Seed4JModuleRank.RANK_D);

  private final String value;
  private final Seed4JModuleRank rank;

  MyCompanyRuntimeExtensionListOnlyModuleSlug(String value, Seed4JModuleRank rank) {
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
