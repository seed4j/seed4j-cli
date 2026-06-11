package com.seed4j.cli.command.domain;

public interface RuntimeExtensionModeSwitcher {
  RuntimeExtensionModeSwitchResult enable();

  RuntimeExtensionModeSwitchResult disable();
}
