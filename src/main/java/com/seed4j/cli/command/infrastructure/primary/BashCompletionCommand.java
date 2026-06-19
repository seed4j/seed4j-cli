package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.BashCompletionInstallApplicationService;
import com.seed4j.cli.command.domain.BashCompletionInstallationException;
import com.seed4j.cli.command.domain.BashCompletionScript;
import com.seed4j.cli.shared.error.domain.Assert;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

@Component
class BashCompletionCommand implements Callable<Integer> {

  private static final String INSTALLATION_INSTRUCTION = "source ~/.local/share/bash-completion/completions/seed4j";
  private static final String INSTALL_OPTION = "--install";

  private final BashCompletionInstallApplicationService installApplicationService;

  private CommandSpec commandSpec;

  BashCompletionCommand(BashCompletionInstallApplicationService installApplicationService) {
    Assert.notNull("installApplicationService", installApplicationService);

    this.installApplicationService = installApplicationService;
  }

  public CommandSpec spec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name(name()).mixinStandardHelpOptions(true);
    spec.usageMessage().description("Print Bash completion script");
    spec.addOption(
      OptionSpec.builder(INSTALL_OPTION)
        .description("Install Bash completion script to ~/.local/share/bash-completion/completions/seed4j")
        .type(Boolean.class)
        .defaultValue("false")
        .build()
    );
    commandSpec = spec;

    return spec;
  }

  public String name() {
    return "bash";
  }

  @Override
  public Integer call() {
    String script = new BashCompletionScriptGenerator().generate(commandSpec.root());
    Boolean install = commandSpec.findOption(INSTALL_OPTION).getValue();

    if (Boolean.TRUE.equals(install)) {
      try {
        installApplicationService.install(new BashCompletionScript(script));
      } catch (BashCompletionInstallationException exception) {
        System.err.println(exception.getMessage());
        return ExitCode.SOFTWARE;
      }
      System.out.println("Installed Bash completion script to ~/.local/share/bash-completion/completions/seed4j");
      System.out.println("Run this command to load it in the current shell:");
      System.out.println(INSTALLATION_INSTRUCTION);

      return ExitCode.OK;
    }

    System.out.print(script);

    return ExitCode.OK;
  }
}
