package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;

@Component
@CommandLine.Command(name = "list", description = "List all jhipster-lite modules")
class ListCommand implements Callable<Integer> {

  private final JHipsterModulesApplicationService modules;

  public ListCommand(JHipsterModulesApplicationService modules) {
    this.modules = modules;
  }

  public Integer call() {
    OutputModuleSlugs moduleSlugs = OutputModuleSlugs.from(modules);
    System.out.printf("Listing all jhipster-lite modules (%s):%n", moduleSlugs.size());
    System.out.println(moduleSlugs.toPrint());

    return 0;
  }
}
