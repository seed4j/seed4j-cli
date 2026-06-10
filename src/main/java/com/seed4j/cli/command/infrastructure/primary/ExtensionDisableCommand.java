package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.RuntimeExtensionModeApplicationService;
import com.seed4j.cli.command.domain.RuntimeExtensionModeSwitchException;
import com.seed4j.cli.command.domain.RuntimeExtensionModeSwitchResult;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;

@Component
class ExtensionDisableCommand implements Callable<Integer> {

  private final RuntimeExtensionModeApplicationService runtimeExtensionModeApplicationService;
  private final CommandSpec commandSpec;

  ExtensionDisableCommand(RuntimeExtensionModeApplicationService runtimeExtensionModeApplicationService) {
    this.runtimeExtensionModeApplicationService = runtimeExtensionModeApplicationService;
    this.commandSpec = buildCommandSpec();
  }

  CommandSpec spec() {
    return commandSpec;
  }

  String name() {
    return "disable";
  }

  @Override
  public Integer call() {
    try {
      RuntimeExtensionModeSwitchResult switchResult = runtimeExtensionModeApplicationService.disable();
      System.out.println("Extension runtime disabled successfully.");
      System.out.printf("Config: %s%n", switchResult.configPath());
      return ExitCode.OK;
    } catch (RuntimeExtensionModeSwitchException exception) {
      System.err.println(exception.getMessage());
      return ExitCode.SOFTWARE;
    }
  }

  private CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name("disable").mixinStandardHelpOptions(true);
    spec.usageMessage().description("Disable active runtime extension");

    return spec;
  }
}
