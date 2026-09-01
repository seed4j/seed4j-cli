package com.seed4j.cli.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaSeed4JCliHomeReader;
import com.seed4j.cli.command.domain.AgentSkillInstallationException;
import com.seed4j.cli.command.domain.AgentSkillInstallationResult;
import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import com.seed4j.cli.command.domain.AgentSkillInstallationStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@UnitTest
class AgentSkillInstallationTest {

  private static final String SKILL_FRONTMATTER = """
  ---
  name: seed4j-cli
  description: Use Seed4J CLI to initialize or modify projects by discovering the active runtime and safely planning and applying modules. Use when Seed4J is the chosen project generator, including implementing a new-project specification, adding Seed4J capabilities, or working with the seed4j command.
  ---
  """;

  @Test
  void shouldPublishTheCompleteBundledSkillForALocalFirstInstallation(@TempDir Path workingDirectory) throws Exception {
    Path destination = workingDirectory.resolve(".agents/skills/seed4j-cli");
    Map<Path, byte[]> bundledFiles = bundledFiles();
    FileSystemAgentSkillInstaller installer = new FileSystemAgentSkillInstaller(
      scope -> destination,
      () -> bundledFiles,
      new NioAgentSkillFileOperations()
    );

    AgentSkillInstallationResult result = installer.install(AgentSkillInstallationScope.LOCAL);

    assertThat(result.status()).isEqualTo(AgentSkillInstallationStatus.INSTALLED);
    assertThat(result.path().path()).isEqualTo(destination.toAbsolutePath().normalize());
    assertThat(Files.readAllBytes(destination.resolve("SKILL.md"))).isEqualTo(bundledFiles.get(Path.of("SKILL.md")));
    assertThat(Files.readAllBytes(destination.resolve("references/applying-modules.md"))).isEqualTo(
      bundledFiles.get(Path.of("references/applying-modules.md"))
    );
    assertThat(Files.readAllBytes(destination.resolve("references/module-set-planning.md"))).isEqualTo(
      bundledFiles.get(Path.of("references/module-set-planning.md"))
    );
    try (Stream<Path> installedFiles = Files.walk(destination)) {
      assertThat(installedFiles.filter(Files::isRegularFile).map(destination::relativize)).containsExactlyInAnyOrderElementsOf(
        bundledFiles.keySet()
      );
    }
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
    Path workingDirectory = temporaryDirectory.resolve("work");
    Path userHome = temporaryDirectory.resolve("home");
    Files.createDirectories(workingDirectory);
    CurrentAgentSkillInstallationPathResolver pathResolver = new CurrentAgentSkillInstallationPathResolver(
      new JavaSeed4JCliHomeReader(new Seed4JCliHome(userHome)),
      () -> workingDirectory
    );
    FileSystemAgentSkillInstaller installer = new FileSystemAgentSkillInstaller(
      pathResolver,
      AgentSkillInstallationTest::bundledFiles,
      new NioAgentSkillFileOperations()
    );
    Path destination = userHome.resolve(".agents/skills/seed4j-cli").toAbsolutePath().normalize();

    AgentSkillInstallationResult result = installer.install(AgentSkillInstallationScope.GLOBAL);

    assertThat(result.status()).isEqualTo(AgentSkillInstallationStatus.INSTALLED);
    assertThat(result.path().path()).isEqualTo(destination);
    assertThat(Files.isDirectory(destination)).isTrue();
    try (Stream<Path> workingDirectoryEntries = Files.list(workingDirectory)) {
      assertThat(workingDirectoryEntries).isEmpty();
    }
  }

  @Test
  void shouldReplaceStaleAndModifiedOwnedContentWhilePreservingSiblingSkills(@TempDir Path skillsDirectory) throws Exception {
    Path destination = skillsDirectory.resolve("seed4j-cli");
    Path siblingSkill = skillsDirectory.resolve("sibling-skill/SKILL.md");
    Files.createDirectories(destination.resolve("references"));
    Files.writeString(destination.resolve("SKILL.md"), "manually modified\n");
    Files.writeString(destination.resolve("references/stale.md"), "stale\n");
    Files.createDirectories(siblingSkill.getParent());
    Files.writeString(siblingSkill, "sibling\n");
    Map<Path, byte[]> bundledFiles = bundledFiles();
    FileSystemAgentSkillInstaller installer = new FileSystemAgentSkillInstaller(
      scope -> destination,
      () -> bundledFiles,
      new NioAgentSkillFileOperations()
    );

    AgentSkillInstallationResult result = installer.install(AgentSkillInstallationScope.LOCAL);

    assertThat(result.status()).isEqualTo(AgentSkillInstallationStatus.UPDATED);
    assertThat(Files.readString(destination.resolve("SKILL.md"))).isEqualTo("skill entrypoint\n");
    assertThat(destination.resolve("references/stale.md")).doesNotExist();
    assertThat(Files.readString(siblingSkill)).isEqualTo("sibling\n");
    try (Stream<Path> operationalResidues = Files.list(skillsDirectory)) {
      assertThat(operationalResidues.map(path -> path.getFileName().toString())).noneMatch(name -> name.startsWith(".seed4j-cli-"));
    }
  }

  @Test
  void shouldLeaveThePreviousInstallationByteEquivalentWhenStagingFails(@TempDir Path skillsDirectory) throws Exception {
    Path destination = skillsDirectory.resolve("seed4j-cli");
    Files.createDirectories(destination.resolve("references"));
    Files.write(destination.resolve("SKILL.md"), new byte[] { 0, 1, 2, 3 });
    Files.write(destination.resolve("references/legacy.md"), new byte[] { 4, 5, 6 });
    FileSystemAgentSkillInstaller installer = new FileSystemAgentSkillInstaller(
      scope -> destination,
      AgentSkillInstallationTest::bundledFiles,
      new WriteFailingFileOperations()
    );

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isInstanceOf(AgentSkillInstallationException.class)
      .hasMessage("Could not install Seed4J CLI skill at %s.".formatted(destination.toAbsolutePath().normalize()))
      .hasCauseInstanceOf(IOException.class);
    assertThat(Files.readAllBytes(destination.resolve("SKILL.md"))).containsExactly(0, 1, 2, 3);
    assertThat(Files.readAllBytes(destination.resolve("references/legacy.md"))).containsExactly(4, 5, 6);
    try (Stream<Path> operationalResidues = Files.list(skillsDirectory)) {
      assertThat(operationalResidues.map(path -> path.getFileName().toString())).noneMatch(name -> name.startsWith(".seed4j-cli-"));
    }
  }

  @Test
  void shouldRestoreThePreviousInstallationWhenPublicationFailsBeforeCommit(@TempDir Path skillsDirectory) throws Exception {
    Path destination = skillsDirectory.resolve("seed4j-cli");
    Path siblingSkill = skillsDirectory.resolve("sibling-skill/SKILL.md");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("SKILL.md"), "previous skill\n");
    Files.createDirectories(siblingSkill.getParent());
    Files.writeString(siblingSkill, "sibling\n");
    FileSystemAgentSkillInstaller installer = new FileSystemAgentSkillInstaller(
      scope -> destination,
      AgentSkillInstallationTest::bundledFiles,
      new MoveFailingFileOperations(Set.of(2))
    );

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isExactlyInstanceOf(AgentSkillInstallationException.class)
      .hasCauseInstanceOf(IOException.class);
    assertThat(Files.readString(destination.resolve("SKILL.md"))).isEqualTo("previous skill\n");
    assertThat(Files.readString(siblingSkill)).isEqualTo("sibling\n");
    try (Stream<Path> operationalResidues = Files.list(skillsDirectory)) {
      assertThat(operationalResidues.map(path -> path.getFileName().toString())).noneMatch(name -> name.startsWith(".seed4j-cli-"));
    }
  }

  @Test
  void shouldDiagnoseThePreservedBackupWhenRestorationAlsoFails(@TempDir Path skillsDirectory) throws Exception {
    Path destination = skillsDirectory.resolve("seed4j-cli");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("SKILL.md"), "previous skill\n");
    FileSystemAgentSkillInstaller installer = new FileSystemAgentSkillInstaller(
      scope -> destination,
      AgentSkillInstallationTest::bundledFiles,
      new MoveFailingFileOperations(Set.of(2, 3))
    );

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isExactlyInstanceOf(AgentSkillInstallationException.class)
      .hasMessageContaining("Previous installation could not be restored")
      .hasMessageContaining(".seed4j-cli-backup-")
      .hasCauseInstanceOf(IOException.class);
    assertThat(destination).doesNotExist();
    try (Stream<Path> skillsEntries = Files.list(skillsDirectory)) {
      assertThat(skillsEntries.map(path -> path.getFileName().toString()))
        .singleElement()
        .asString()
        .startsWith(".seed4j-cli-backup-");
    }
  }

  @Test
  void shouldKeepTheCommittedUpdateAndDiagnoseResidualBackupWhenCleanupFails(@TempDir Path skillsDirectory) throws Exception {
    Path destination = skillsDirectory.resolve("seed4j-cli");
    Path siblingSkill = skillsDirectory.resolve("sibling-skill/SKILL.md");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("SKILL.md"), "previous skill\n");
    Files.createDirectories(siblingSkill.getParent());
    Files.writeString(siblingSkill, "sibling\n");
    Map<Path, byte[]> bundledFiles = bundledFiles();
    FileSystemAgentSkillInstaller installer = new FileSystemAgentSkillInstaller(
      scope -> destination,
      () -> bundledFiles,
      new DeleteFailingFileOperations()
    );

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isInstanceOf(AgentSkillInstallationException.class)
      .hasMessageContaining("The updated skill remains installed at %s".formatted(destination.toAbsolutePath().normalize()))
      .hasMessageContaining("Backup remains at")
      .hasCauseInstanceOf(IOException.class);
    assertThat(Files.readAllBytes(destination.resolve("SKILL.md"))).isEqualTo(bundledFiles.get(Path.of("SKILL.md")));
    assertThat(Files.readString(siblingSkill)).isEqualTo("sibling\n");
    try (Stream<Path> skillsEntries = Files.list(skillsDirectory)) {
      assertThat(skillsEntries.map(path -> path.getFileName().toString())).anyMatch(name -> name.startsWith(".seed4j-cli-backup-"));
    }
  }

  @Test
  void shouldReplaceAnOwnedDestinationSymlinkWithoutFollowingIt(@TempDir Path temporaryDirectory) throws Exception {
    Path skillsDirectory = temporaryDirectory.resolve("skills");
    Path externalSkill = temporaryDirectory.resolve("external-skill");
    Path destination = skillsDirectory.resolve("seed4j-cli");
    Files.createDirectories(skillsDirectory);
    Files.createDirectories(externalSkill);
    Files.writeString(externalSkill.resolve("SKILL.md"), "external skill\n");
    Files.createSymbolicLink(destination, externalSkill);
    FileSystemAgentSkillInstaller installer = new FileSystemAgentSkillInstaller(
      scope -> destination,
      AgentSkillInstallationTest::bundledFiles,
      new NioAgentSkillFileOperations()
    );

    AgentSkillInstallationResult result = installer.install(AgentSkillInstallationScope.LOCAL);

    assertThat(result.status()).isEqualTo(AgentSkillInstallationStatus.UPDATED);
    assertThat(Files.isSymbolicLink(destination)).isFalse();
    assertThat(Files.readString(destination.resolve("SKILL.md"))).isEqualTo("skill entrypoint\n");
    assertThat(Files.readString(externalSkill.resolve("SKILL.md"))).isEqualTo("external skill\n");
  }

  private static Map<Path, byte[]> bundledFiles() {
    Map<Path, byte[]> files = new LinkedHashMap<>();
    files.put(Path.of("SKILL.md"), "skill entrypoint\n".getBytes(StandardCharsets.UTF_8));
    files.put(Path.of("references/applying-modules.md"), "individual module\n".getBytes(StandardCharsets.UTF_8));
    files.put(Path.of("references/module-set-planning.md"), "module set\n".getBytes(StandardCharsets.UTF_8));
    return Map.copyOf(files);
  }

  private static final class WriteFailingFileOperations extends NioAgentSkillFileOperations {

    @Override
    public void write(Path path, byte[] content) throws IOException {
      throw new IOException("staging denied");
    }
  }

  private static final class MoveFailingFileOperations extends NioAgentSkillFileOperations {

    private final Set<Integer> failingMoves;
    private int moveCount;

    private MoveFailingFileOperations(Set<Integer> failingMoves) {
      this.failingMoves = failingMoves;
    }

    @Override
    public void move(Path source, Path destination) throws IOException {
      moveCount++;
      if (failingMoves.contains(moveCount)) {
        throw new IOException("move denied");
      }
      super.move(source, destination);
    }
  }

  private static final class DeleteFailingFileOperations extends NioAgentSkillFileOperations {

    @Override
    public void delete(Path path) throws IOException {
      throw new IOException("cleanup denied");
    }
  }
}
