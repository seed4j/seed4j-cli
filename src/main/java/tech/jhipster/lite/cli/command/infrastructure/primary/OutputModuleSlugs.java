package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.Collection;
import java.util.stream.Collectors;
import tech.jhipster.lite.cli.shared.error.domain.Assert;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.domain.resource.JHipsterModuleResource;

record OutputModuleSlugs(Collection<OutputModuleSlug> moduleSlugs) {
  public OutputModuleSlugs {
    Assert.notNull("moduleSlugs", moduleSlugs);
  }

  public static OutputModuleSlugs from(JHipsterModulesApplicationService modules) {
    return new OutputModuleSlugs(
      modules.resources().stream().map(JHipsterModuleResource::slug).map(slug -> new OutputModuleSlug(slug.toString())).toList()
    );
  }

  public String toPrint() {
    return moduleSlugs().stream().map(OutputModuleSlug::slug).collect(Collectors.joining("\n"));
  }
}
