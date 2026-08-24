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
class BashCompletionCommand {

  private static final String INSTALLATION_INSTRUCTION = "source ~/.local/share/bash-completion/completions/seed4j";
  private static final String INSTALL_OPTION = "--install";
  private static final String NO_COMPLETE_VALUES_OPTION = "--no-complete-values";

  private final BashCompletionInstallApplicationService installApplicationService;

  BashCompletionCommand(BashCompletionInstallApplicationService installApplicationService) {
    Assert.notNull("installApplicationService", installApplicationService);

    this.installApplicationService = installApplicationService;
  }

  public CommandSpec spec() {
    return new BashCompletionInvocation(installApplicationService, name()).spec();
  }

  public String name() {
    return "bash";
  }

  private static final class BashCompletionInvocation implements Callable<Integer> {

    private final BashCompletionInstallApplicationService installApplicationService;
    private final CommandSpec commandSpec;
    private final OptionSpec installOption;
    private final OptionSpec noCompleteValuesOption;

    private BashCompletionInvocation(BashCompletionInstallApplicationService installApplicationService, String commandName) {
      this.installApplicationService = installApplicationService;
      installOption = OptionSpec.builder(INSTALL_OPTION)
        .description("Install Bash completion script to ~/.local/share/bash-completion/completions/seed4j")
        .type(Boolean.class)
        .defaultValue("false")
        .build();
      noCompleteValuesOption = OptionSpec.builder(NO_COMPLETE_VALUES_OPTION)
        .description("Generate Bash completion without option value candidates")
        .type(Boolean.class)
        .defaultValue("false")
        .build();
      commandSpec = CommandSpec.wrapWithoutInspection(this).name(commandName).mixinStandardHelpOptions(true);
      commandSpec.usageMessage().description("Print Bash completion script");
      commandSpec.addOption(installOption);
      commandSpec.addOption(noCompleteValuesOption);
    }

    private CommandSpec spec() {
      return commandSpec;
    }

    @Override
    public Integer call() {
      String script = new BashCompletionScriptGenerator().generate(commandSpec.root(), valueCompletion());

      if (Boolean.TRUE.equals(installOption.getValue())) {
        return install(script);
      }

      System.out.print(script);

      return ExitCode.OK;
    }

    private int install(String script) {
      try {
        installApplicationService.install(new BashCompletionScript(script));
      } catch (BashCompletionInstallationException exception) {
        System.err.println(exception.getMessage());
        return ExitCode.SOFTWARE;
      }
      printInstallationInstructions();

      return ExitCode.OK;
    }

    private void printInstallationInstructions() {
      System.out.println("Installed Bash completion script to ~/.local/share/bash-completion/completions/seed4j");
      System.out.println("Run this command to load it in the current shell:");
      System.out.println(INSTALLATION_INSTRUCTION);
    }

    private BashCompletionValueCompletion valueCompletion() {
      return BashCompletionValueCompletion.from(!Boolean.TRUE.equals(noCompleteValuesOption.getValue()));
    }
  }
}
