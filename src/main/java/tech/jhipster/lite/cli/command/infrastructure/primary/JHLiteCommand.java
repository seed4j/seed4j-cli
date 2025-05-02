package tech.jhipster.lite.cli.command.infrastructure.primary;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
  name = "jhlite",
  mixinStandardHelpOptions = true,
  subcommands = ListCommand.class,
  description = "JHLite CLI Application",
  headerHeading = "%n",
  commandListHeading = "%nCommands:%n"
)
class JHLiteCommand {}
