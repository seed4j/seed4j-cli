package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.RuntimeExtensionModeSwitchResult;
import com.seed4j.cli.command.domain.RuntimeExtensionModeSwitcher;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Service;

@Service
public class RuntimeExtensionModeApplicationService {

  private final RuntimeExtensionModeSwitcher runtimeExtensionModeSwitcher;

  public RuntimeExtensionModeApplicationService(RuntimeExtensionModeSwitcher runtimeExtensionModeSwitcher) {
    Assert.notNull("runtimeExtensionModeSwitcher", runtimeExtensionModeSwitcher);

    this.runtimeExtensionModeSwitcher = runtimeExtensionModeSwitcher;
  }

  public RuntimeExtensionModeSwitchResult enable() {
    return runtimeExtensionModeSwitcher.enable();
  }

  public RuntimeExtensionModeSwitchResult disable() {
    return runtimeExtensionModeSwitcher.disable();
  }
}
