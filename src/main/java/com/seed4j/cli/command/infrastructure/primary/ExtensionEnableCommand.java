package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.RuntimeExtensionModeApplicationService;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class ExtensionEnableCommand implements Callable<Integer> {

  private final RuntimeExtensionModeCommand command;

  ExtensionEnableCommand(RuntimeExtensionModeApplicationService runtimeExtensionModeApplicationService) {
    this.command = new RuntimeExtensionModeCommand(
      this,
      "enable",
      "Enable active runtime extension",
      "Extension runtime enabled successfully.",
      runtimeExtensionModeApplicationService::enable
    );
  }

  CommandSpec spec() {
    return command.spec();
  }

  String name() {
    return command.name();
  }

  @Override
  public Integer call() {
    return command.call();
  }
}
