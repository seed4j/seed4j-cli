package com.seed4j.cli.command.infrastructure.primary;

enum BashCompletionValueCompletion {
  ENABLED,
  DISABLED;

  static BashCompletionValueCompletion from(boolean completeValues) {
    if (completeValues) {
      return ENABLED;
    }

    return DISABLED;
  }

  boolean enabled() {
    return this == ENABLED;
  }
}
