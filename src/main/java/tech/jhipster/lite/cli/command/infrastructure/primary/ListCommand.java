package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "list", description = "List all jhipster-lite modules")
class ListCommand implements Callable<Integer> {

  @CommandLine.ParentCommand
  private JHLiteCommand jhlite;

  public Integer call() {
    OutputModuleSlugs moduleSlugs = OutputModuleSlugs.from(jhlite.modules());
    System.out.printf("Listing all jhipster-lite modules (%s):%n", moduleSlugs.moduleSlugs().size());
    System.out.println(moduleSlugs.toPrint());

    return 0;
  }
}
