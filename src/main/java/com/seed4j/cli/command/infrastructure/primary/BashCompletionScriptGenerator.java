package com.seed4j.cli.command.infrastructure.primary;

import picocli.CommandLine.Model.CommandSpec;

class BashCompletionScriptGenerator {

  public String generate(CommandSpec rootCommand, BashCompletionValueCompletion valueCompletion) {
    BashCompletionCandidates candidates = new BashCompletionCandidateCollector().collect(rootCommand);

    return new BashCompletionScriptRenderer().render(candidates, valueCompletion);
  }
}
