package tech.jhipster.lite.cli.command.infrastructure.primary;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class JHLiteCommandsFactory {

  private final ListModulesCommand listModulesCommand;
  private final ApplyModuleCommand applyModuleCommand;

  public JHLiteCommandsFactory(ListModulesCommand listModulesCommand, ApplyModuleCommand applyModuleCommand) {
    this.listModulesCommand = listModulesCommand;
    this.applyModuleCommand = applyModuleCommand;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.create().name("jhlite").mixinStandardHelpOptions(true);

    spec.usageMessage().description("JHipster Lite CLI").headerHeading("%n").commandListHeading("%nCommands:%n");

    spec.addSubcommand("list", listModulesCommand.buildCommandSpec());
    spec.addSubcommand("apply", applyModuleCommand.buildCommandSpec());

    return spec;
  }
}
