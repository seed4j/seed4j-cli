package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.Comparator;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.domain.resource.JHipsterModuleResource;

@Component
class ApplyModuleCommand {

  private final JHipsterModulesApplicationService modules;

  public ApplyModuleCommand(JHipsterModulesApplicationService modules) {
    this.modules = modules;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec spec = createSpec();
    addModuleSlugSubcommands(spec);

    return spec;
  }

  private CommandSpec createSpec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name("apply").mixinStandardHelpOptions(true);
    spec.usageMessage().description("Apply jhipster-lite specific module");

    return spec;
  }

  private void addModuleSlugSubcommands(CommandSpec spec) {
    modules
      .resources()
      .stream()
      .sorted(byModuleSlug())
      .forEach(module -> spec.addSubcommand(module.slug().get(), new ModuleSlugCommand(modules, module).commandSpec()));
  }

  private static Comparator<JHipsterModuleResource> byModuleSlug() {
    return Comparator.comparing(jHipsterModuleResource -> jHipsterModuleResource.slug().get());
  }
}
