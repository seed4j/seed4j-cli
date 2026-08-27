package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetPlanItem(ModuleSetSlug slug, ModuleSetApplicationKind applicationKind) {
  public ModuleSetPlanItem {
    Assert.notNull("slug", slug);
    Assert.notNull("applicationKind", applicationKind);
  }

  public boolean reapplied() {
    return applicationKind.reapplied();
  }
}
