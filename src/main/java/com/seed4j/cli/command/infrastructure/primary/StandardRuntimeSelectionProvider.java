package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.bootstrap.RuntimeMode;
import com.seed4j.cli.bootstrap.RuntimeSelection;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class StandardRuntimeSelectionProvider implements RuntimeSelectionProvider {

  @Override
  public RuntimeSelection runtimeSelection() {
    return new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());
  }
}
