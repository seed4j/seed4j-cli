package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;

@Component
@CommandLine.Command(name = "list", description = "List all jhipster-lite modules")
class ListModulesCommand implements Callable<Integer> {

  private final JHipsterModulesApplicationService modules;

  public ListModulesCommand(JHipsterModulesApplicationService modules) {
    this.modules = modules;
  }

  public Integer call() {
    OutputModuleSlugs moduleSlugs = OutputModuleSlugs.from(modules.resources());
    System.out.printf("Listing all jhipster-lite modules (%s):%n", moduleSlugs.size());
    System.out.println(toPrint(moduleSlugs));

    return ExitCode.OK;
  }

  private String toPrint(OutputModuleSlugs moduleSlugs) {
    return moduleSlugs.stream().map(OutputModuleSlug::slug).collect(Collectors.joining(System.lineSeparator()));
  }
}
