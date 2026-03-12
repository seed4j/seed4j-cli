package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.bootstrap.RuntimeSelection;

@FunctionalInterface
interface RuntimeSelectionProvider {
  RuntimeSelection runtimeSelection();
}
