package tech.jhipster.lite.cli.command.infrastructure.primary;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
  name = "jhlite",
  mixinStandardHelpOptions = true,
  subcommands = { ListModulesCommand.class, ApplyModuleCommand.class },
  description = "JHipster Lite CLI",
  headerHeading = "%n",
  commandListHeading = "%nCommands:%n"
)
class JHLiteCommand {}
