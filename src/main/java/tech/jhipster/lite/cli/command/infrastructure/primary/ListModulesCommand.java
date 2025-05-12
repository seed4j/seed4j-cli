package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.domain.JHipsterSlug;
import tech.jhipster.lite.module.domain.resource.JHipsterModuleResource;
import tech.jhipster.lite.module.domain.resource.JHipsterModulesResources;

@Component
class ListModulesCommand implements Callable<Integer> {

  private static final int MINIMAL_SPACES_BETWEEN_SLUG_AND_DESCRIPTION = 2;

  private final JHipsterModulesApplicationService modules;

  public ListModulesCommand(JHipsterModulesApplicationService modules) {
    this.modules = modules;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name("list");
    spec.usageMessage().description("List available jhipster-lite modules");

    return spec;
  }

  @Override
  public Integer call() {
    JHipsterModulesResources modulesResources = modules.resources();
    System.out.printf("Available jhipster-lite modules (%s):%n", modulesResources.stream().count());
    modulesResources.stream().sorted(byModuleSlug()).forEach(printModule(maxSlugLength(modulesResources)));

    return CommandLine.ExitCode.OK;
  }

  private static Comparator<JHipsterModuleResource> byModuleSlug() {
    return Comparator.comparing(jHipsterModuleResource -> jHipsterModuleResource.slug().get());
  }

  private int maxSlugLength(JHipsterModulesResources modulesResources) {
    return modulesResources.stream().map(JHipsterModuleResource::slug).map(JHipsterSlug::get).mapToInt(String::length).max().orElse(0);
  }

  private Consumer<? super JHipsterModuleResource> printModule(int maxSlugLength) {
    return moduleResource -> {
      String spacesBetweenSlugAndDescription =
        " ".repeat(maxSlugLength - moduleResource.slug().get().length() + MINIMAL_SPACES_BETWEEN_SLUG_AND_DESCRIPTION);
      System.out.printf("  %s%s%s%n", moduleResource.slug(), spacesBetweenSlugAndDescription, moduleResource.apiDoc().operation());
    };
  }
}
