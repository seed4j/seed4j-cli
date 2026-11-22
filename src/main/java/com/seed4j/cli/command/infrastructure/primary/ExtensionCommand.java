package com.seed4j.cli.command.infrastructure.primary;

import java.util.List;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;

@Component
class ExtensionCommand implements Seed4JCommand {

  private static final String TRANSITION = "Loading runtime extension JARs is no longer supported.";

  @Override
  public CommandSpec spec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name(name()).mixinStandardHelpOptions(true);
    spec.usageMessage().description(TRANSITION);

    for (String action : List.of("install", "enable", "disable")) {
      CommandSpec retired = CommandSpec.wrapWithoutInspection((Callable<Integer>) ExtensionCommand::retired)
        .name(action)
        .mixinStandardHelpOptions(true);
      retired.usageMessage().description(TRANSITION);
      retired.parser().unmatchedArgumentsAllowed(true);
      spec.addSubcommand(action, retired);
    }

    return spec;
  }

  private static int retired() {
    System.err.println(TRANSITION);
    return ExitCode.USAGE;
  }

  @Override
  public String name() {
    return "extension";
  }
}
