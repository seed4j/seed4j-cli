package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.AgentSkillInstallApplicationService;
import com.seed4j.cli.command.domain.AgentSkillInstallationException;
import com.seed4j.cli.command.domain.AgentSkillInstallationResult;
import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

@Component
class SkillInstallCommand implements Callable<Integer> {

  private static final String GLOBAL_OPTION = "--global";

  private final AgentSkillInstallApplicationService applicationService;
  private final CommandSpec commandSpec;

  SkillInstallCommand(AgentSkillInstallApplicationService applicationService) {
    this.applicationService = applicationService;
    commandSpec = buildCommandSpec();
  }

  CommandSpec spec() {
    return commandSpec;
  }

  String name() {
    return "install";
  }

  @Override
  public Integer call() {
    try {
      AgentSkillInstallationResult result = applicationService.install(scope());
      System.out.printf("%s Seed4J CLI skill at %s.%n", statusText(result), result.path().path());
      return ExitCode.OK;
    } catch (AgentSkillInstallationException exception) {
      System.err.println(exception.getMessage());
      return ExitCode.SOFTWARE;
    }
  }

  private AgentSkillInstallationScope scope() {
    if (Boolean.TRUE.equals(commandSpec.findOption(GLOBAL_OPTION).getValue())) {
      return AgentSkillInstallationScope.GLOBAL;
    }
    return AgentSkillInstallationScope.LOCAL;
  }

  private static String statusText(AgentSkillInstallationResult result) {
    return switch (result.status()) {
      case INSTALLED -> "Installed";
      case UPDATED -> "Updated";
    };
  }

  private CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name(name()).mixinStandardHelpOptions(true);
    spec.usageMessage().description("Install the bundled Seed4J CLI agent skill locally by default");
    spec.addOption(
      OptionSpec.builder(GLOBAL_OPTION)
        .description("Install for the current user instead of the local working directory")
        .type(Boolean.class)
        .defaultValue("false")
        .build()
    );
    return spec;
  }
}
