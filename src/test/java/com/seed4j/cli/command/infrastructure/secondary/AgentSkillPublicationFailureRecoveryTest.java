package com.seed4j.cli.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.domain.AgentSkillInstallationException;
import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@UnitTest
class AgentSkillPublicationFailureRecoveryTest {

  @Test
  void shouldRemoveStagingWhenFirstInstallationPublicationFails(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writeSiblingSkill();
    FileSystemAgentSkillInstaller installer = fixture.installer(AgentSkillInstallationFailures.firstInstallationPublication());

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isExactlyInstanceOf(AgentSkillInstallationException.class)
      .hasMessage("Could not install Seed4J CLI skill at %s.".formatted(fixture.destination().toAbsolutePath().normalize()))
      .hasCauseInstanceOf(IOException.class);
    assertThat(fixture.destination()).doesNotExist();
    assertThat(Files.readString(fixture.siblingSkill())).isEqualTo("sibling\n");
    assertThat(fixture.skillEntries()).noneMatch(name -> name.startsWith(".seed4j-cli-"));
  }

  @Test
  void shouldPreserveThePreviousInstallationWhenBackupPublicationFails(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writePreviousBinarySkill();
    fixture.writeSiblingSkill();
    FileSystemAgentSkillInstaller installer = fixture.installer(AgentSkillInstallationFailures.previousInstallationBackup());

    assertThatThrownBy(() -> installer.install(AgentSkillInstallationScope.LOCAL))
      .isExactlyInstanceOf(AgentSkillInstallationException.class)
      .hasMessage("Could not install Seed4J CLI skill at %s.".formatted(fixture.destination().toAbsolutePath().normalize()))
      .hasCauseInstanceOf(IOException.class);
    assertThat(fixture.destinationFileSnapshot()).isEqualTo(fixture.previousBinaryFileSnapshot());
    assertThat(Files.readString(fixture.siblingSkill())).isEqualTo("sibling\n");
    assertThat(fixture.skillEntries()).noneMatch(name -> name.startsWith(".seed4j-cli-"));
  }

  @Test
  void shouldNotReportCleanupFailureWhenStagingDisappearsDuringFailedPublication(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writeSiblingSkill();
    FileSystemAgentSkillInstaller installer = fixture.installer(
      AgentSkillInstallationFailures.stagingDisappearsDuringFirstInstallationPublication()
    );

    Throwable failure = catchThrowable(() -> installer.install(AgentSkillInstallationScope.LOCAL));

    assertGenericPublicationFailureWithoutRecoveryDiagnostic(failure, fixture);
    assertThat(fixture.destination()).doesNotExist();
    assertThat(Files.readString(fixture.siblingSkill())).isEqualTo("sibling\n");
    assertThat(fixture.skillEntries()).noneMatch(name -> name.startsWith(".seed4j-cli-"));
  }

  private void assertGenericPublicationFailureWithoutRecoveryDiagnostic(Throwable failure, AgentSkillInstallationFixture fixture) {
    assertThat(failure)
      .isExactlyInstanceOf(AgentSkillInstallationException.class)
      .hasMessage("Could not install Seed4J CLI skill at %s.".formatted(fixture.destination().toAbsolutePath().normalize()))
      .hasCauseInstanceOf(IOException.class);
    assertThat(failure.getCause().getSuppressed()).isEmpty();
  }

  @Test
  void shouldNotDiagnoseBackupResidueWhenBackupDisappearsDuringFailedUpdatePublication(@TempDir Path skillsDirectory) throws Exception {
    AgentSkillInstallationFixture fixture = new AgentSkillInstallationFixture(skillsDirectory);
    fixture.writePreviousBinarySkill();
    fixture.writeSiblingSkill();
    FileSystemAgentSkillInstaller installer = fixture.installer(
      AgentSkillInstallationFailures.backupDisappearsDuringUpdatePublication(fixture.destination())
    );

    Throwable failure = catchThrowable(() -> installer.install(AgentSkillInstallationScope.LOCAL));

    assertGenericPublicationFailureWithoutRecoveryDiagnostic(failure, fixture);
    assertThat(fixture.destination()).doesNotExist();
    assertThat(Files.readString(fixture.siblingSkill())).isEqualTo("sibling\n");
    assertThat(fixture.skillEntries()).noneMatch(name -> name.startsWith(".seed4j-cli-"));
  }
}
