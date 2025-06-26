package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class JHLiteCommandsFactory {

  private final List<JHLiteCommand> jhliteCommands;
  private final String version;
  private final String jHLiteVersion;

  public JHLiteCommandsFactory(
    List<JHLiteCommand> jhliteCommands,
    @Value("${project.version}") String version,
    @Value("${project.jhlite-version}") String jHLiteVersion
  ) {
    this.jhliteCommands = jhliteCommands;
    this.version = version;
    this.jHLiteVersion = jHLiteVersion;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.create()
      .name("jhlite")
      .mixinStandardHelpOptions(true)
      .version(
        """
        JHipster Lite CLI v%s
        JHipster Lite version: %s""".formatted(version, jHLiteVersion)
      );

    spec.usageMessage().description("JHipster Lite CLI").headerHeading("%n").commandListHeading("%nCommands:%n");

    jhliteCommands.forEach(command -> spec.addSubcommand(command.name(), command.spec()));

    return spec;
  }
}
