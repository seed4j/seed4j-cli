package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.Comparator;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.domain.resource.JHipsterModuleResource;

@Component
class ApplyModuleCommand implements JHLiteCommand {

  private final JHipsterModulesApplicationService modules;
  private final ApplyModuleSubCommandsFactory subCommandsFactory;

  public ApplyModuleCommand(JHipsterModulesApplicationService modules, ApplyModuleSubCommandsFactory subCommandsFactory) {
    this.modules = modules;
    this.subCommandsFactory = subCommandsFactory;
  }

  @Override
  public CommandSpec spec() {
    CommandSpec spec = createSpec();
    addModuleSlugSubcommands(spec);

    return spec;
  }

  @Override
  public String name() {
    return "apply";
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
      .forEach(module -> spec.addSubcommand(module.slug().get(), subCommandsFactory.create(module).commandSpec()));
  }

  private static Comparator<JHipsterModuleResource> byModuleSlug() {
    return Comparator.comparing(jHipsterModuleResource -> jHipsterModuleResource.slug().get());
  }
}
