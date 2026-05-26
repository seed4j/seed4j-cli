package com.seed4j.cli.command.infrastructure.primary;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class ExtensionCommand implements Seed4JCommand {

  private final ExtensionInstallCommand extensionInstallCommand;

  ExtensionCommand(ExtensionInstallCommand extensionInstallCommand) {
    this.extensionInstallCommand = extensionInstallCommand;
  }

  @Override
  public CommandSpec spec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name("extension").mixinStandardHelpOptions(true);
    spec.usageMessage().description("Manage runtime extensions");
    spec.addSubcommand(extensionInstallCommand.name(), extensionInstallCommand.spec());

    return spec;
  }

  @Override
  public String name() {
    return "extension";
  }
}
