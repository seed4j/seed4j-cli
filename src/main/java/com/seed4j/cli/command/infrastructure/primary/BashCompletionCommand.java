package com.seed4j.cli.command.infrastructure.primary;

import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;

@Component
class BashCompletionCommand implements Callable<Integer> {

  private CommandSpec commandSpec;

  public CommandSpec spec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name(name()).mixinStandardHelpOptions(true);
    spec.usageMessage().description("Print Bash completion script");
    commandSpec = spec;

    return spec;
  }

  public String name() {
    return "bash";
  }

  @Override
  public Integer call() {
    System.out.print(new BashCompletionScriptGenerator().generate(commandSpec.root()));

    return ExitCode.OK;
  }
}
