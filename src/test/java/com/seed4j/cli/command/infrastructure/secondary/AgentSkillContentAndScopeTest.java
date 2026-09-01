package com.seed4j.cli.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaSeed4JCliHomeReader;
import com.seed4j.cli.command.domain.AgentSkillInstallationResult;
import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import com.seed4j.cli.command.domain.AgentSkillInstallationStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@UnitTest
class AgentSkillContentAndScopeTest {

  private static final String SKILL_FRONTMATTER = """
  ---
  name: seed4j-cli
  description: Use Seed4J CLI to initialize or modify projects by discovering the active runtime and safely planning and applying modules. Use when Seed4J is the chosen project generator, including implementing a new-project specification, adding Seed4J capabilities, or working with the seed4j command.
  ---
  """;

  @Test
  void shouldPublishTheCompleteBundledSkillForALocalFirstInstallation(@TempDir Path workingDirectory) throws Exception {
    ScopedInstallation installation = scopedInstallation(
      workingDirectory,
      workingDirectory.resolve("home"),
      workingDirectory.resolve(".agents/skills")
    );

    AgentSkillInstallationResult result = installation.installer().install(AgentSkillInstallationScope.LOCAL);

    assertThat(result.status()).isEqualTo(AgentSkillInstallationStatus.INSTALLED);
    assertThat(result.path().path()).isEqualTo(installation.fixture().destination().toAbsolutePath().normalize());
    assertThat(installation.fixture().destinationFileSnapshot()).isEqualTo(installation.fixture().bundledFileSnapshot());
  }

  @Test
  void shouldReadOnlyTheThreeCanonicalSkillResourcesFromTheCliCodeSource() throws Exception {
    BaseJarAgentSkillResources resources = new BaseJarAgentSkillResources();

    Map<Path, byte[]> bundledFiles = resources.read();

    assertThat(bundledFiles.keySet()).containsExactlyInAnyOrder(
      Path.of("SKILL.md"),
      Path.of("references/applying-modules.md"),
      Path.of("references/module-set-planning.md")
    );
    assertThat(new String(bundledFiles.get(Path.of("SKILL.md")), StandardCharsets.UTF_8)).startsWith(SKILL_FRONTMATTER);
    assertThat(new String(bundledFiles.get(Path.of("references/applying-modules.md")), StandardCharsets.UTF_8))
      .contains("seed4j apply <module> --plan")
      .contains("approval_policy = \"never\"")
      .contains("default_permissions = \":danger-full-access\"");
    assertThat(new String(bundledFiles.get(Path.of("references/module-set-planning.md")), StandardCharsets.UTF_8))
      .contains("seed4j apply-set <modules...> --plan")
      .contains("partial failure");
  }

  @Test
  void shouldKeepSemanticDiscoveryWithinTheSeed4JProjectGenerationBoundary() throws Exception {
    String skill = new String(new BaseJarAgentSkillResources().read().get(Path.of("SKILL.md")), StandardCharsets.UTF_8);
    String normalizedSkill = skill.replaceAll("\\s+", " ");

    assertThat(normalizedSkill)
      .contains("Use this skill when Seed4J is the chosen project generator")
      .contains("Do not use it for ordinary application bugs that do not involve Seed4J modules")
      .contains("authoring a new Seed4J module or runtime extension")
      .contains("another project generator");
  }

  @Test
  void shouldInstallGloballyUnderTheConfiguredCliHomeWithoutTouchingTheWorkingDirectory(@TempDir Path temporaryDirectory) throws Exception {
    Path userHome = temporaryDirectory.resolve("home");
    ScopedInstallation installation = scopedInstallation(temporaryDirectory.resolve("work"), userHome, userHome.resolve(".agents/skills"));

    AgentSkillInstallationResult result = installation.installer().install(AgentSkillInstallationScope.GLOBAL);

    assertThat(result.status()).isEqualTo(AgentSkillInstallationStatus.INSTALLED);
    assertThat(result.path().path()).isEqualTo(installation.fixture().destination().toAbsolutePath().normalize());
    assertThat(Files.isDirectory(installation.fixture().destination())).isTrue();
    try (Stream<Path> workingDirectoryEntries = Files.list(installation.workingDirectory())) {
      assertThat(workingDirectoryEntries).isEmpty();
    }
  }

  private static ScopedInstallation scopedInstallation(Path workingDirectory, Path userHome, Path skillsDirectory) throws IOException {
    Files.createDirectories(workingDirectory);
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    CurrentAgentSkillInstallationPathResolver pathResolver = new CurrentAgentSkillInstallationPathResolver(
      new JavaSeed4JCliHomeReader(new Seed4JCliHome(userHome)),
      workingDirectory
    );
    FileSystemAgentSkillInstaller installer = new FileSystemAgentSkillInstaller(
      pathResolver,
      fixture::bundledFiles,
      new NioAgentSkillFileOperations()
    );
    return new ScopedInstallation(workingDirectory, fixture, installer);
  }

  private record ScopedInstallation(
    Path workingDirectory,
    AgentSkillInstallationFixture fixture,
    FileSystemAgentSkillInstaller installer
  ) {}
}
