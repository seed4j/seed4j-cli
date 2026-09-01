package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.AgentSkillInstallationException;
import com.seed4j.cli.command.domain.AgentSkillInstallationStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class AgentSkillPublication {

  private static final String BACKUP_PREFIX = ".seed4j-cli-backup-";
  private static final String STAGING_PREFIX = ".seed4j-cli-staging-";

  private final Path destination;
  private final BundledAgentSkillResources bundledResources;
  private final AgentSkillFileOperations fileOperations;

  AgentSkillPublication(Path destination, BundledAgentSkillResources bundledResources, AgentSkillFileOperations fileOperations) {
    this.destination = destination;
    this.bundledResources = bundledResources;
    this.fileOperations = fileOperations;
  }

  AgentSkillInstallationStatus publish() {
    try {
      return publish(installationStatus());
    } catch (IOException exception) {
      throw installationFailure(exception, RecoveryOutcome.empty());
    }
  }

  private AgentSkillInstallationStatus installationStatus() {
    return fileOperations.exists(destination) ? AgentSkillInstallationStatus.UPDATED : AgentSkillInstallationStatus.INSTALLED;
  }

  private AgentSkillInstallationStatus publish(AgentSkillInstallationStatus status) throws IOException {
    fileOperations.createDirectories(destination.getParent());
    StagedPublication publication = stage();
    return switch (status) {
      case INSTALLED -> publishFirstInstallation(publication);
      case UPDATED -> publishUpdate(publication);
    };
  }

  private StagedPublication stage() throws IOException {
    Path staging = fileOperations.createTemporaryDirectory(destination.getParent(), STAGING_PREFIX);
    try {
      writeBundledResources(staging);
      return new StagedPublication(staging);
    } catch (IOException exception) {
      throw installationFailure(exception, recover(new StagedPublication(staging), exception));
    }
  }

  private void writeBundledResources(Path staging) throws IOException {
    for (Map.Entry<Path, byte[]> resource : bundledResources.read().entrySet()) {
      fileOperations.write(staging.resolve(resource.getKey()), resource.getValue());
    }
  }

  private AgentSkillInstallationStatus publishFirstInstallation(StagedPublication publication) {
    try {
      fileOperations.move(publication.staging(), destination);
      return AgentSkillInstallationStatus.INSTALLED;
    } catch (IOException exception) {
      throw installationFailure(exception, recover(publication, exception));
    }
  }

  private AgentSkillInstallationStatus publishUpdate(StagedPublication publication) {
    UpdatePublication update = backUpPreviousInstallation(publication);
    try {
      fileOperations.move(update.staging(), destination);
    } catch (IOException exception) {
      throw installationFailure(exception, recover(update, exception));
    }
    cleanBackupAfterCommit(update);
    return AgentSkillInstallationStatus.UPDATED;
  }

  private UpdatePublication backUpPreviousInstallation(StagedPublication publication) {
    Path backup = destination.resolveSibling(BACKUP_PREFIX + UUID.randomUUID());
    try {
      fileOperations.move(destination, backup);
      return new UpdatePublication(publication.staging(), backup);
    } catch (IOException exception) {
      throw installationFailure(exception, recover(publication, exception));
    }
  }

  private void cleanBackupAfterCommit(UpdatePublication update) {
    try {
      fileOperations.delete(update.backup());
    } catch (IOException exception) {
      throw postCommitCleanupFailure(update.backup(), exception);
    }
  }

  private PostCommitCleanupException postCommitCleanupFailure(Path backup, IOException cause) {
    String residualBackup = fileOperations.exists(backup) ? " Backup remains at %s.".formatted(backup) : "";
    String message = "Could not clean up the previous Seed4J CLI skill. The updated skill remains installed at %s.%s".formatted(
      destination,
      residualBackup
    );
    return new PostCommitCleanupException(message, cause);
  }

  private RecoveryOutcome recover(StagedPublication publication, IOException failure) {
    return new RecoveryOutcome(Optional.empty(), cleanStaging(publication.staging(), failure));
  }

  private RecoveryOutcome recover(UpdatePublication publication, IOException failure) {
    Optional<Path> unrestoredBackup = restorePreviousInstallation(publication.backup(), failure);
    Optional<Path> residualStaging = cleanStaging(publication.staging(), failure);
    return new RecoveryOutcome(unrestoredBackup, residualStaging);
  }

  private Optional<Path> restorePreviousInstallation(Path backup, IOException failure) {
    if (!fileOperations.exists(backup)) {
      return Optional.empty();
    }
    try {
      fileOperations.move(backup, destination);
      return Optional.empty();
    } catch (IOException restorationFailure) {
      failure.addSuppressed(restorationFailure);
      return Optional.of(backup);
    }
  }

  private Optional<Path> cleanStaging(Path staging, IOException failure) {
    if (!fileOperations.exists(staging)) {
      return Optional.empty();
    }
    try {
      fileOperations.delete(staging);
      return Optional.empty();
    } catch (IOException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
      return Optional.of(staging).filter(fileOperations::exists);
    }
  }

  private AgentSkillInstallationException installationFailure(IOException cause, RecoveryOutcome recovery) {
    return new AgentSkillInstallationException(
      "Could not install Seed4J CLI skill at %s.%s".formatted(destination, recovery.diagnostic()),
      cause
    );
  }

  private record StagedPublication(Path staging) {}

  private record UpdatePublication(Path staging, Path backup) {}

  private record RecoveryOutcome(Optional<Path> unrestoredBackup, Optional<Path> residualStaging) {
    private static RecoveryOutcome empty() {
      return new RecoveryOutcome(Optional.empty(), Optional.empty());
    }

    private String diagnostic() {
      String backupDiagnostic = unrestoredBackup.map(RecoveryOutcome::unrestoredBackupDiagnostic).orElse("");
      String stagingDiagnostic = residualStaging.map(RecoveryOutcome::residualStagingDiagnostic).orElse("");
      return backupDiagnostic + stagingDiagnostic;
    }

    private static String unrestoredBackupDiagnostic(Path backup) {
      return " Previous installation could not be restored. Backup remains at %s.".formatted(backup);
    }

    private static String residualStagingDiagnostic(Path staging) {
      return " Staging remains at %s.".formatted(staging);
    }
  }

  private static final class PostCommitCleanupException extends AgentSkillInstallationException {

    private PostCommitCleanupException(String message, IOException cause) {
      super(message, cause);
    }
  }
}
