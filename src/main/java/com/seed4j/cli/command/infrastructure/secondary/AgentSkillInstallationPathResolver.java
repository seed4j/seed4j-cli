package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import java.nio.file.Path;

@FunctionalInterface
interface AgentSkillInstallationPathResolver {
  Path resolve(AgentSkillInstallationScope scope);
}
