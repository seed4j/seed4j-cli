package com.seed4j.cli.command.infrastructure.primary;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class CompletionCommand implements Seed4JCommand {

  private final BashCompletionCommand bashCompletionCommand;

  public CompletionCommand(BashCompletionCommand bashCompletionCommand) {
    this.bashCompletionCommand = bashCompletionCommand;
  }

  @Override
  public CommandSpec spec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name(name()).mixinStandardHelpOptions(true);
    spec.usageMessage().description("Generate shell completion scripts");
    spec.addSubcommand(bashCompletionCommand.name(), bashCompletionCommand.spec());

    return spec;
  }

  @Override
  public String name() {
    return "completion";
  }
}
