package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.AgentSkillInstallationResult;
import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import com.seed4j.cli.command.domain.AgentSkillInstaller;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Service;

@Service
public class AgentSkillInstallApplicationService {

  private final AgentSkillInstaller agentSkillInstaller;

  public AgentSkillInstallApplicationService(AgentSkillInstaller agentSkillInstaller) {
    Assert.notNull("agentSkillInstaller", agentSkillInstaller);
    this.agentSkillInstaller = agentSkillInstaller;
  }

  public AgentSkillInstallationResult install(AgentSkillInstallationScope scope) {
    Assert.notNull("scope", scope);
    return agentSkillInstaller.install(scope);
  }
}
