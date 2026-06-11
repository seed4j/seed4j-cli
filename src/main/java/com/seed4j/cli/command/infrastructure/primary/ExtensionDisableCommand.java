package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.RuntimeExtensionModeApplicationService;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class ExtensionDisableCommand implements Callable<Integer> {

  private final RuntimeExtensionModeCommand command;

  ExtensionDisableCommand(RuntimeExtensionModeApplicationService runtimeExtensionModeApplicationService) {
    this.command = new RuntimeExtensionModeCommand(
      this,
      "disable",
      "Disable active runtime extension",
      "Extension runtime disabled successfully.",
      runtimeExtensionModeApplicationService::disable
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
