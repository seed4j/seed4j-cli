package com.seed4j.cli.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.domain.AgentSkillInstallationException;
import com.seed4j.cli.command.domain.AgentSkillInstallationResult;
import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import com.seed4j.cli.command.domain.AgentSkillInstallationStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@UnitTest
class AgentSkillUpdateAndRecoveryTest {

  @Test
  void shouldReplaceStaleAndModifiedOwnedContentWhilePreservingSiblingSkills(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writeModifiedSkill();
    fixture.writeSiblingSkill();

    AgentSkillInstallationResult result = fixture.installer(new NioAgentSkillFileOperations()).install(AgentSkillInstallationScope.LOCAL);

    assertThat(result.status()).isEqualTo(AgentSkillInstallationStatus.UPDATED);
    assertThat(fixture.destinationFileSnapshot()).isEqualTo(fixture.bundledFileSnapshot());
    assertThat(fixture.destination().resolve("references/stale.md")).doesNotExist();
    assertThat(Files.readString(fixture.siblingSkill())).isEqualTo("sibling\n");
    assertThat(fixture.skillEntries()).noneMatch(name -> name.startsWith(".seed4j-cli-"));
  }

  @Test
  void shouldLeaveThePreviousInstallationByteEquivalentWhenStagingFails(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writePreviousBinarySkill();
    FileSystemAgentSkillInstaller installer = fixture.installer(AgentSkillInstallationFailures.stagingWrite());

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isInstanceOf(AgentSkillInstallationException.class)
      .hasMessage("Could not install Seed4J CLI skill at %s.".formatted(fixture.destination().toAbsolutePath().normalize()))
      .hasCauseInstanceOf(IOException.class);
    assertThat(fixture.destinationFileSnapshot()).isEqualTo(fixture.previousBinaryFileSnapshot());
    assertThat(fixture.skillEntries()).noneMatch(name -> name.startsWith(".seed4j-cli-"));
  }

  @Test
  void shouldLeaveThePreviousInstallationUntouchedWhenStagingCannotBePrepared(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writePreviousBinarySkill();
    fixture.writeSiblingSkill();
    FileSystemAgentSkillInstaller installer = fixture.installer(AgentSkillInstallationFailures.stagingPreparation());

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isExactlyInstanceOf(AgentSkillInstallationException.class)
      .hasMessage("Could not install Seed4J CLI skill at %s.".formatted(fixture.destination().toAbsolutePath().normalize()))
      .hasCauseInstanceOf(IOException.class);
    assertThat(fixture.destinationFileSnapshot()).isEqualTo(fixture.previousBinaryFileSnapshot());
    assertThat(Files.readString(fixture.siblingSkill())).isEqualTo("sibling\n");
    assertThat(fixture.skillEntries()).noneMatch(name -> name.startsWith(".seed4j-cli-"));
  }

  @Test
  void shouldRestoreThePreviousInstallationWhenPublicationFailsBeforeCommit(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writePreviousTextSkill();
    fixture.writeSiblingSkill();
    FileSystemAgentSkillInstaller installer = fixture.installer(AgentSkillInstallationFailures.publication());

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isExactlyInstanceOf(AgentSkillInstallationException.class)
      .hasMessage("Could not install Seed4J CLI skill at %s.".formatted(fixture.destination().toAbsolutePath().normalize()))
      .hasCauseInstanceOf(IOException.class);
    assertThat(fixture.destinationFileSnapshot()).isEqualTo(fixture.previousTextFileSnapshot());
    assertThat(Files.readString(fixture.siblingSkill())).isEqualTo("sibling\n");
    assertThat(fixture.skillEntries()).noneMatch(name -> name.startsWith(".seed4j-cli-"));
  }

  @Test
  void shouldDiagnoseResidualStagingWhenItsCleanupFailsAfterRestoration(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writePreviousBinarySkill();
    fixture.writeSiblingSkill();
    FileSystemAgentSkillInstaller installer = fixture.installer(AgentSkillInstallationFailures.publicationAndStagingCleanup());

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isExactlyInstanceOf(AgentSkillInstallationException.class)
      .hasMessageContaining("Staging remains at")
      .hasMessageNotContaining("Backup remains at")
      .hasCauseInstanceOf(IOException.class);
    assertThat(fixture.destinationFileSnapshot()).isEqualTo(fixture.previousBinaryFileSnapshot());
    assertThat(Files.readString(fixture.siblingSkill())).isEqualTo("sibling\n");
    assertThat(fixture.skillEntries())
      .anyMatch(name -> name.startsWith(".seed4j-cli-staging-"))
      .noneMatch(name -> name.startsWith(".seed4j-cli-backup-"));
  }

  @Test
  void shouldDiagnoseThePreservedBackupWhenRestorationAlsoFails(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writePreviousTextSkill();
    FileSystemAgentSkillInstaller installer = fixture.installer(AgentSkillInstallationFailures.publicationAndRestoration());

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isExactlyInstanceOf(AgentSkillInstallationException.class)
      .hasMessageContaining("Previous installation could not be restored")
      .hasMessageContaining(".seed4j-cli-backup-")
      .hasCauseInstanceOf(IOException.class);
    assertThat(fixture.destination()).doesNotExist();
    assertThat(fixture.skillEntries()).singleElement().asString().startsWith(".seed4j-cli-backup-");
  }

  @Test
  void shouldKeepTheCommittedUpdateAndDiagnoseResidualBackupWhenCleanupFails(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writePreviousTextSkill();
    fixture.writeSiblingSkill();
    FileSystemAgentSkillInstaller installer = fixture.installer(AgentSkillInstallationFailures.backupCleanup());

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isInstanceOf(AgentSkillInstallationException.class)
      .hasMessageContaining("The updated skill remains installed at %s".formatted(fixture.destination().toAbsolutePath().normalize()))
      .hasMessageContaining("Backup remains at")
      .hasCauseInstanceOf(IOException.class);
    assertThat(fixture.destinationFileSnapshot()).isEqualTo(fixture.bundledFileSnapshot());
    assertThat(Files.readString(fixture.siblingSkill())).isEqualTo("sibling\n");
    assertThat(fixture.skillEntries()).anyMatch(name -> name.startsWith(".seed4j-cli-backup-"));
  }

  @Test
  void shouldKeepTheCommittedUpdateWithoutDiagnosingAnAlreadyRemovedBackup(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writePreviousTextSkill();
    fixture.writeSiblingSkill();
    FileSystemAgentSkillInstaller installer = fixture.installer(AgentSkillInstallationFailures.removedBackupCleanup());

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isInstanceOf(AgentSkillInstallationException.class)
      .hasMessageContaining("The updated skill remains installed at %s".formatted(fixture.destination().toAbsolutePath().normalize()))
      .hasMessageNotContaining("Backup remains at")
      .hasCauseInstanceOf(IOException.class);
    assertThat(fixture.destinationFileSnapshot()).isEqualTo(fixture.bundledFileSnapshot());
    assertThat(Files.readString(fixture.siblingSkill())).isEqualTo("sibling\n");
    assertThat(fixture.skillEntries()).noneMatch(name -> name.startsWith(".seed4j-cli-"));
  }

  @Test
  void shouldReplaceAnOwnedDestinationSymlinkWithoutFollowingIt(@TempDir Path temporaryDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(temporaryDirectory.resolve("skills"));
    Path externalSkill = createDestinationSymlinkToExternalSkill(temporaryDirectory, fixture);

    AgentSkillInstallationResult result = fixture.installer(new NioAgentSkillFileOperations()).install(AgentSkillInstallationScope.LOCAL);

    assertThat(result.status()).isEqualTo(AgentSkillInstallationStatus.UPDATED);
    assertThat(Files.isSymbolicLink(fixture.destination())).isFalse();
    assertThat(fixture.destinationFileSnapshot()).isEqualTo(fixture.bundledFileSnapshot());
    assertThat(Files.readString(externalSkill.resolve("SKILL.md"))).isEqualTo("external skill\n");
  }

  private static Path createDestinationSymlinkToExternalSkill(Path temporaryDirectory, AgentSkillInstallationFixture fixture)
    throws IOException {
    Path externalSkill = temporaryDirectory.resolve("external-skill");
    Files.createDirectories(fixture.skillsDirectory());
    Files.createDirectories(externalSkill);
    Files.writeString(externalSkill.resolve("SKILL.md"), "external skill\n");
    Files.createSymbolicLink(fixture.destination(), externalSkill);
    return externalSkill;
  }
}
