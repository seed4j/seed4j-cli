package tech.jhipster.lite.cli.command.infrastructure.primary;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;

@Component
@Command(
  name = "jhlite",
  mixinStandardHelpOptions = true,
  subcommands = ListCommand.class,
  description = "JHLite CLI Application",
  headerHeading = "%n",
  commandListHeading = "%nCommands:%n"
)
class JHLiteCommand {

  private final JHipsterModulesApplicationService modules;

  public JHLiteCommand(JHipsterModulesApplicationService modules) {
    this.modules = modules;
  }

  public JHipsterModulesApplicationService modules() {
    return modules;
  }
}
