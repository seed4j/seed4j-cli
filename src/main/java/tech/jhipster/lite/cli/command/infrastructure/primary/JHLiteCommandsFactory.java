package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.List;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class JHLiteCommandsFactory {

  private final List<JHLiteCommand> jhliteCommands;

  public JHLiteCommandsFactory(List<JHLiteCommand> jhliteCommands) {
    this.jhliteCommands = jhliteCommands;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.create().name("jhlite").mixinStandardHelpOptions(true);

    spec.usageMessage().description("JHipster Lite CLI").headerHeading("%n").commandListHeading("%nCommands:%n");

    jhliteCommands.forEach(command -> spec.addSubcommand(command.name(), command.spec()));

    return spec;
  }
}
