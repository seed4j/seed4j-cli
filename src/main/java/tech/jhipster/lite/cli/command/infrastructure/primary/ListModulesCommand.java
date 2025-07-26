package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.domain.JHipsterSlug;
import tech.jhipster.lite.module.domain.resource.JHipsterModuleResource;
import tech.jhipster.lite.module.domain.resource.JHipsterModulesResources;

@Component
class ListModulesCommand implements JHLiteCommand, Callable<Integer> {

  private static final int MINIMAL_SPACES_BETWEEN_SLUG_AND_DESCRIPTION = 2;

  private final JHipsterModulesApplicationService modules;
  private final CommandSpec spec;

  public ListModulesCommand(JHipsterModulesApplicationService modules) {
    this.modules = modules;
    this.spec = buildCommandSpec();
  }

  private CommandSpec buildCommandSpec() {
    CommandSpec commandSpec = CommandSpec.wrapWithoutInspection(this).name("list");
    commandSpec.usageMessage().description("List available jhipster-lite modules");

    return commandSpec;
  }

  @Override
  public CommandSpec spec() {
    return spec;
  }

  @Override
  public String name() {
    return "list";
  }

  @Override
  public Integer call() {
    JHipsterModulesResources modulesResources = modules.resources();
    spec.commandLine().getOut().printf("Available jhipster-lite modules (%s):%n", modulesResources.stream().count());
    modulesResources.stream().sorted(byModuleSlug()).forEach(printModule(maxSlugLength(modulesResources)));

    return ExitCode.OK;
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
      spec
        .commandLine()
        .getOut()
        .printf("  %s%s%s%n", moduleResource.slug(), spacesBetweenSlugAndDescription, moduleResource.apiDoc().operation());
    };
  }
}
