package com.seed4j.cli.bootstrap.domain.runtimeextension.apply;

import com.seed4j.module.domain.resource.Seed4JModuleRank;
import com.seed4j.module.domain.resource.Seed4JModuleSlugFactory;

public enum RuntimeExtensionApplySharedContextModuleSlug implements Seed4JModuleSlugFactory {
  RUNTIME_EXTENSION_APPLY_SHARED_CONTEXT("runtime-extension-apply-shared-context", Seed4JModuleRank.RANK_D);

  private final String slug;
  private final Seed4JModuleRank rank;

  RuntimeExtensionApplySharedContextModuleSlug(String slug, Seed4JModuleRank rank) {
    this.slug = slug;
    this.rank = rank;
  }

  @Override
  public String get() {
    return slug;
  }

  @Override
  public Seed4JModuleRank rank() {
    return rank;
  }
}
