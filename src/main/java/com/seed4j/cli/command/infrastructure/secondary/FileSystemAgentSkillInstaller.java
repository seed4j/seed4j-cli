package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.AgentSkillInstallationPath;
import com.seed4j.cli.command.domain.AgentSkillInstallationResult;
import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import com.seed4j.cli.command.domain.AgentSkillInstallationStatus;
import com.seed4j.cli.command.domain.AgentSkillInstaller;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

class FileSystemAgentSkillInstaller implements AgentSkillInstaller {

  private final AgentSkillInstallationPathResolver installationPathResolver;
  private final BundledAgentSkillResources bundledResources;
  private final AgentSkillFileOperations fileOperations;

  FileSystemAgentSkillInstaller(
    AgentSkillInstallationPathResolver installationPathResolver,
    BundledAgentSkillResources bundledResources,
    AgentSkillFileOperations fileOperations
  ) {
    Assert.notNull("installationPathResolver", installationPathResolver);
    Assert.notNull("bundledResources", bundledResources);
    Assert.notNull("fileOperations", fileOperations);
    this.installationPathResolver = installationPathResolver;
    this.bundledResources = bundledResources;
    this.fileOperations = fileOperations;
  }

  @Override
  public AgentSkillInstallationResult install(AgentSkillInstallationScope scope) {
    Assert.notNull("scope", scope);
    Path destination = installationPathResolver.resolve(scope).toAbsolutePath().normalize();
    AgentSkillInstallationStatus status = new AgentSkillPublication(destination, bundledResources, fileOperations).publish();
    return new AgentSkillInstallationResult(status, new AgentSkillInstallationPath(destination));
  }
}
