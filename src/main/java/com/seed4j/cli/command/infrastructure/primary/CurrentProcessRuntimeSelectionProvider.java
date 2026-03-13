package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.bootstrap.RuntimeSelection;
import org.springframework.stereotype.Component;

@Component
class CurrentProcessRuntimeSelectionProvider implements RuntimeSelectionProvider {

  private final RuntimeSystemProperties runtimeSystemProperties;

  CurrentProcessRuntimeSelectionProvider(RuntimeSystemProperties runtimeSystemProperties) {
    this.runtimeSystemProperties = runtimeSystemProperties;
  }

  @Override
  public RuntimeSelection runtimeSelection() {
    return new SystemPropertyRuntimeSelectionProvider(runtimeSystemProperties.values()).runtimeSelection();
  }
}
