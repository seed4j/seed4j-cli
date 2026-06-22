package com.seed4j.cli.command.infrastructure.primary;

enum ModuleParameterSource {
  EXPLICIT("explicit CLI input"),
  PROJECT_HISTORY("project history"),
  DEFAULT("default");

  private final String displayLabel;

  ModuleParameterSource(String displayLabel) {
    this.displayLabel = displayLabel;
  }

  String displayLabel() {
    return displayLabel;
  }

  boolean projectHistory() {
    return this == PROJECT_HISTORY;
  }
}
