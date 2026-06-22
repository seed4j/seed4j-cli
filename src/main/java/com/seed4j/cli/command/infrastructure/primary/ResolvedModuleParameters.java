package com.seed4j.cli.command.infrastructure.primary;

import java.util.List;

record ResolvedModuleParameters(List<ResolvedModuleParameter> parameters, List<MissingRequiredModuleParameter> missingRequiredParameters) {
  boolean empty() {
    return parameters.isEmpty();
  }

  boolean complete() {
    return missingRequiredParameters.isEmpty();
  }
}
