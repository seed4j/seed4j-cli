package tech.jhipster.lite.cli.command.infrastructure.primary;

import tech.jhipster.lite.cli.shared.error.domain.Assert;

record OutputModuleSlug(String slug) {
  public OutputModuleSlug {
    Assert.notBlank("slug", slug);
  }
}
