package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeSelectionReader;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeSelectionReader.JavaRuntimeSelection;
import com.seed4j.cli.command.domain.RuntimeDisplay;
import com.seed4j.cli.command.domain.RuntimeDisplayReader;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Component;

@Component
class BootstrapRuntimeDisplayReader implements RuntimeDisplayReader {

  private final JavaRuntimeSelectionReader runtimeSelectionReader;

  BootstrapRuntimeDisplayReader(JavaRuntimeSelectionReader runtimeSelectionReader) {
    Assert.notNull("runtimeSelectionReader", runtimeSelectionReader);

    this.runtimeSelectionReader = runtimeSelectionReader;
  }

  @Override
  public RuntimeDisplay activeRuntime() {
    JavaRuntimeSelection runtimeSelection = runtimeSelectionReader.activeRuntimeSelection();

    if (runtimeSelection.extension()) {
      return RuntimeDisplay.extension(runtimeSelection.distributionId(), runtimeSelection.distributionVersion());
    }

    return RuntimeDisplay.standard();
  }
}
