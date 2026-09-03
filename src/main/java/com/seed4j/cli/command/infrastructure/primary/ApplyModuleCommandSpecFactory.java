package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.module.domain.resource.Seed4JModulePropertiesDefinition;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

class ApplyModuleCommandSpecFactory {

  static final String PROJECT_PATH_OPTION = ProjectPathOptionSpecFactory.OPTION_NAME;
  static final String COMMIT_OPTION = "--commit";
  static final String PLAN_OPTION = "--plan";

  CommandSpec create(ApplyModuleSubCommand command, Seed4JModuleResource module) {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(command).name(module.slug().get()).mixinStandardHelpOptions(true);
    spec.usageMessage().description(module.apiDoc().operation().get().replace("%", "%%"));
    addOptions(spec, module.propertiesDefinition());
    return spec;
  }

  private static void addOptions(CommandSpec spec, Seed4JModulePropertiesDefinition properties) {
    spec.addOption(new ProjectPathOptionSpecFactory().create());
    spec.addOption(commitOption());
    spec.addOption(planOption());
    ModulePropertyOptionSpecFactory optionsFactory = new ModulePropertyOptionSpecFactory();
    properties.stream().map(optionsFactory::moduleOption).forEach(spec::addOption);
  }

  private static OptionSpec commitOption() {
    return OptionSpec.builder(COMMIT_OPTION)
      .description("Initialize Git if needed and commit generated changes; --no-commit skips Git init and commit")
      .negatable(true)
      .type(Boolean.class)
      .build();
  }

  private static OptionSpec planOption() {
    return OptionSpec.builder(PLAN_OPTION)
      .description("Print the resolved module parameters and value sources without applying changes")
      .type(Boolean.class)
      .build();
  }
}
