package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.bootstrap.infrastructure.primary.JavaSeed4JCliHomeReader;
import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

class CurrentAgentSkillInstallationPathResolver implements AgentSkillInstallationPathResolver {

  private static final Path SKILL_PATH = Path.of(".agents/skills/seed4j-cli");

  private final JavaSeed4JCliHomeReader cliHomeReader;
  private final WorkingDirectoryReader workingDirectoryReader;

  CurrentAgentSkillInstallationPathResolver(JavaSeed4JCliHomeReader cliHomeReader, WorkingDirectoryReader workingDirectoryReader) {
    Assert.notNull("cliHomeReader", cliHomeReader);
    Assert.notNull("workingDirectoryReader", workingDirectoryReader);
    this.cliHomeReader = cliHomeReader;
    this.workingDirectoryReader = workingDirectoryReader;
  }

  @Override
  public Path resolve(AgentSkillInstallationScope scope) {
    Assert.notNull("scope", scope);
    Path base = switch (scope) {
      case LOCAL -> workingDirectoryReader.current();
      case GLOBAL -> cliHomeReader.path();
    };
    return base.resolve(SKILL_PATH).toAbsolutePath().normalize();
  }
}
