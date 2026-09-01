package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.bootstrap.infrastructure.primary.JavaSeed4JCliHomeReader;
import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class CurrentAgentSkillInstallationPathResolver implements AgentSkillInstallationPathResolver {

  private static final Path SKILL_PATH = Path.of(".agents/skills/seed4j-cli");

  private final JavaSeed4JCliHomeReader cliHomeReader;
  private final Path workingDirectory;

  CurrentAgentSkillInstallationPathResolver(JavaSeed4JCliHomeReader cliHomeReader, @Value("${user.dir}") String workingDirectory) {
    Assert.notNull("cliHomeReader", cliHomeReader);
    Assert.notNull("workingDirectory", workingDirectory);
    this.cliHomeReader = cliHomeReader;
    this.workingDirectory = Path.of(workingDirectory);
  }

  @Override
  public Path resolve(AgentSkillInstallationScope scope) {
    Assert.notNull("scope", scope);
    Path base = switch (scope) {
      case LOCAL -> workingDirectory;
      case GLOBAL -> cliHomeReader.path();
    };
    return base.resolve(SKILL_PATH).toAbsolutePath().normalize();
  }
}
