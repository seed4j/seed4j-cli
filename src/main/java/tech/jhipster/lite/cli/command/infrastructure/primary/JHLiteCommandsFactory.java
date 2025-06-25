package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class JHLiteCommandsFactory {

  private final List<JHLiteCommand> jhliteCommands;
  private final String version;

  public JHLiteCommandsFactory(List<JHLiteCommand> jhliteCommands, @Value("${project.version:0.0.1-SNAPSHOT}") String version) {
    this.jhliteCommands = jhliteCommands;
    this.version = version;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.create()
      .name("jhlite")
      .mixinStandardHelpOptions(true)
      .version("JHipster Lite CLI v%s".formatted(version));

    spec.usageMessage().description("JHipster Lite CLI").headerHeading("%n").commandListHeading("%nCommands:%n");

    jhliteCommands.forEach(command -> spec.addSubcommand(command.name(), command.spec()));

    return spec;
  }
}
