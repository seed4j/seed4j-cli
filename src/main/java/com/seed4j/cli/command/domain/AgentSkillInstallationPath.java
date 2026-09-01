package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record AgentSkillInstallationPath(Path path) {
  public AgentSkillInstallationPath {
    Assert.notNull("path", path);
    path = path.toAbsolutePath().normalize();
  }
}
