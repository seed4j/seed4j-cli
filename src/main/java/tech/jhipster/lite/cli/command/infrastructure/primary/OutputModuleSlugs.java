package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.Collection;
import java.util.stream.Stream;
import tech.jhipster.lite.cli.shared.error.domain.Assert;
import tech.jhipster.lite.module.domain.resource.JHipsterModuleResource;
import tech.jhipster.lite.module.domain.resource.JHipsterModulesResources;

record OutputModuleSlugs(Collection<OutputModuleSlug> moduleSlugs) {
  public OutputModuleSlugs {
    Assert.notNull("moduleSlugs", moduleSlugs);
  }

  public static OutputModuleSlugs from(JHipsterModulesResources modulesResources) {
    return new OutputModuleSlugs(
      modulesResources.stream().map(JHipsterModuleResource::slug).map(slug -> new OutputModuleSlug(slug.get())).toList()
    );
  }

  public Integer size() {
    return moduleSlugs().size();
  }

  public Stream<OutputModuleSlug> stream() {
    return moduleSlugs().stream();
  }
}
