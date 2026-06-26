package com.seed4j.cli.command.infrastructure.primary;

import java.util.List;

record ApplyModuleDependencyPlan(List<ApplyModuleDependencyPlanLine> lines) {
  boolean empty() {
    return lines.isEmpty();
  }
}
