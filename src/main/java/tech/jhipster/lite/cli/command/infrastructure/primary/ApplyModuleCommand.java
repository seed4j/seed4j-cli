package tech.jhipster.lite.cli.command.infrastructure.primary;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;

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
      .forEach(module -> spec.addSubcommand(module.slug().get(), new ModuleSlugCommand(modules, module).commandSpec()));
  }
}
