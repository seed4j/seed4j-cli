package com.seed4j.cli.command.infrastructure.primary;

enum ApplyModulePlanItemMarker {
  RESOLVED("✓"),
  PENDING("○");

  private final String symbol;

  ApplyModulePlanItemMarker(String symbol) {
    this.symbol = symbol;
  }

  String prefix() {
    return symbol + " ";
  }
}
