package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.RuntimeExtensionModeSwitchException;
import com.seed4j.cli.command.domain.RuntimeExtensionModeSwitchResult;
import com.seed4j.cli.shared.error.domain.Assert;
import java.util.function.Supplier;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;

final class RuntimeExtensionModeCommand {

  private final String name;
  private final String successMessage;
  private final Supplier<RuntimeExtensionModeSwitchResult> switchRuntimeMode;
  private final CommandSpec commandSpec;

  RuntimeExtensionModeCommand(
    Object owner,
    String name,
    String description,
    String successMessage,
    Supplier<RuntimeExtensionModeSwitchResult> switchRuntimeMode
  ) {
    Assert.notNull("owner", owner);
    Assert.notNull("name", name);
    Assert.notNull("description", description);
    Assert.notNull("successMessage", successMessage);
    Assert.notNull("switchRuntimeMode", switchRuntimeMode);

    this.name = name;
    this.successMessage = successMessage;
    this.switchRuntimeMode = switchRuntimeMode;
    this.commandSpec = buildCommandSpec(owner, description);
  }

  CommandSpec spec() {
    return commandSpec;
  }

  String name() {
    return name;
  }

  Integer call() {
    try {
      RuntimeExtensionModeSwitchResult switchResult = switchRuntimeMode.get();
      System.out.println(successMessage);
      System.out.printf("Config: %s%n", switchResult.configPath());
      return ExitCode.OK;
    } catch (RuntimeExtensionModeSwitchException exception) {
      System.err.println(exception.getMessage());
      return ExitCode.SOFTWARE;
    }
  }

  private CommandSpec buildCommandSpec(Object owner, String description) {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(owner).name(name).mixinStandardHelpOptions(true);
    spec.usageMessage().description(description);

    return spec;
  }
}
