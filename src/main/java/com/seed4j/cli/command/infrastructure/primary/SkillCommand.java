package com.seed4j.cli.command.infrastructure.primary;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class SkillCommand implements Seed4JCommand {

  private final SkillInstallCommand skillInstallCommand;

  SkillCommand(SkillInstallCommand skillInstallCommand) {
    this.skillInstallCommand = skillInstallCommand;
  }

  @Override
  public CommandSpec spec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name(name()).mixinStandardHelpOptions(true);
    spec.usageMessage().description("Manage agent skills");
    spec.addSubcommand(skillInstallCommand.name(), skillInstallCommand.spec());
    return spec;
  }

  @Override
  public String name() {
    return "skill";
  }
}
