package com.seed4j.cli.command.infrastructure.primary;

import java.util.List;

record ApplyModuleDependencyPlan(List<ApplyModuleDependencyPlanLine> lines) {
  boolean empty() {
    return lines.isEmpty();
  }

  boolean notReady() {
    return lines.stream().anyMatch(line -> !line.status().satisfied());
  }

  List<ApplyModuleDependencyPlanLine> pendingLines() {
    return lines
      .stream()
      .filter(line -> !line.status().satisfied())
      .toList();
  }
}
