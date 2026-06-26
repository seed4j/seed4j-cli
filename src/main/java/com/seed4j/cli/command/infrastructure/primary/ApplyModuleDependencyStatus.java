package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.List;

record ApplyModuleDependencyStatus(StatusKind kind, String moduleSlug, List<String> candidates) {
  ApplyModuleDependencyStatus {
    candidates = List.copyOf(candidates);
  }

  static ApplyModuleDependencyStatus alreadyApplied() {
    return new ApplyModuleDependencyStatus(StatusKind.ALREADY_APPLIED, "", List.of());
  }

  static ApplyModuleDependencyStatus pending() {
    return new ApplyModuleDependencyStatus(StatusKind.PENDING, "", List.of());
  }

  static ApplyModuleDependencyStatus satisfiedBy(String moduleSlug) {
    return new ApplyModuleDependencyStatus(StatusKind.SATISFIED_BY, moduleSlug, List.of());
  }

  static ApplyModuleDependencyStatus pendingChoice(List<String> candidates) {
    Assert.notEmpty("candidates", candidates);

    return new ApplyModuleDependencyStatus(StatusKind.PENDING_CHOICE, "", candidates);
  }

  String displayLabel() {
    return switch (kind) {
      case ALREADY_APPLIED -> "already applied";
      case PENDING -> "pending";
      case SATISFIED_BY -> "satisfied by " + moduleSlug;
      case PENDING_CHOICE -> "pending choice: " + String.join(", ", candidates);
    };
  }

  private enum StatusKind {
    ALREADY_APPLIED,
    PENDING,
    SATISFIED_BY,
    PENDING_CHOICE,
  }
}
