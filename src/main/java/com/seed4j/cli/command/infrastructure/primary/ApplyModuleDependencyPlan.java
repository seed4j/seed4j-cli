package com.seed4j.cli.command.infrastructure.primary;

import java.util.List;

record ApplyModuleDependencyPlan(List<ApplyModuleDependencyPlanLine> lines) {
  boolean empty() {
    return lines.isEmpty();
  }

  boolean ready() {
    return lines.stream().allMatch(line -> line.status().satisfied());
  }

  List<ApplyModuleDependencyPlanLine> pendingLines() {
    return lines
      .stream()
      .filter(line -> !line.status().satisfied())
      .toList();
  }
}
