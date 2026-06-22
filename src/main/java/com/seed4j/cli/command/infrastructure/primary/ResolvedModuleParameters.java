package com.seed4j.cli.command.infrastructure.primary;

import java.util.List;

record ResolvedModuleParameters(List<ResolvedModuleParameter> parameters) {
  boolean empty() {
    return parameters.isEmpty();
  }
}
