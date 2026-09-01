package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record AgentSkillInstallationResult(AgentSkillInstallationStatus status, AgentSkillInstallationPath path) {
  public AgentSkillInstallationResult {
    Assert.notNull("status", status);
    Assert.notNull("path", path);
  }
}
