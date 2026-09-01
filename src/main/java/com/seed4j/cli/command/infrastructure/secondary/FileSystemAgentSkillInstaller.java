package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.AgentSkillInstallationException;
import com.seed4j.cli.command.domain.AgentSkillInstallationPath;
import com.seed4j.cli.command.domain.AgentSkillInstallationResult;
import com.seed4j.cli.command.domain.AgentSkillInstallationScope;
import com.seed4j.cli.command.domain.AgentSkillInstallationStatus;
import com.seed4j.cli.command.domain.AgentSkillInstaller;
import com.seed4j.cli.shared.error.domain.Assert;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class FileSystemAgentSkillInstaller implements AgentSkillInstaller {

  private static final String BACKUP_PREFIX = ".seed4j-cli-backup-";
  private static final String STAGING_PREFIX = ".seed4j-cli-staging-";

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
    Path skillsDirectory = destination.getParent();
    AgentSkillInstallationStatus status = fileOperations.exists(destination)
      ? AgentSkillInstallationStatus.UPDATED
      : AgentSkillInstallationStatus.INSTALLED;
    PublicationProgress progress = PublicationProgress.start(destination);

    try {
      fileOperations.createDirectories(skillsDirectory);
      progress = progress.withStaging(fileOperations.createTemporaryDirectory(skillsDirectory, STAGING_PREFIX));
      stage(progress.requiredStaging());
      if (status == AgentSkillInstallationStatus.UPDATED) {
        progress = movePreviousInstallation(progress);
      }
      fileOperations.move(progress.requiredStaging(), destination);
      progress = progress.afterCommit();
      cleanBackupAfterCommit(progress);
      return new AgentSkillInstallationResult(status, new AgentSkillInstallationPath(destination));
    } catch (PostCommitCleanupException exception) {
      throw exception;
    } catch (IOException exception) {
      RecoveryOutcome recovery = recover(progress, exception);
      throw new AgentSkillInstallationException(
        "Could not install Seed4J CLI skill at %s.%s".formatted(destination, recovery.diagnostic()),
        exception
      );
    }
  }

  private void stage(Path staging) throws IOException {
    for (Map.Entry<Path, byte[]> resource : bundledResources.read().entrySet()) {
      fileOperations.write(staging.resolve(resource.getKey()), resource.getValue());
    }
  }

  private PublicationProgress movePreviousInstallation(PublicationProgress progress) throws IOException {
    Path backup = progress.destination().resolveSibling(BACKUP_PREFIX + UUID.randomUUID());
    fileOperations.move(progress.destination(), backup);
    return progress.withBackup(backup);
  }

  private void cleanBackupAfterCommit(PublicationProgress progress) {
    if (progress.backup().isEmpty()) {
      return;
    }
    Path backup = progress.backup().orElseThrow();
    try {
      fileOperations.delete(backup);
    } catch (IOException exception) {
      String residualBackup = fileOperations.exists(backup) ? " Backup remains at %s.".formatted(backup) : "";
      throw new PostCommitCleanupException(
        "Could not clean up the previous Seed4J CLI skill. The updated skill remains installed at %s.%s".formatted(
          progress.destination(),
          residualBackup
        ),
        exception
      );
    }
  }

  private RecoveryOutcome recover(PublicationProgress progress, IOException failure) {
    Optional<Path> unrestoredBackup = restorePreviousInstallation(progress, failure);
    Optional<Path> residualStaging = cleanStaging(progress, failure);
    return new RecoveryOutcome(unrestoredBackup, residualStaging);
  }

  private Optional<Path> restorePreviousInstallation(PublicationProgress progress, IOException failure) {
    Optional<Path> availableBackup = progress.backup().filter(fileOperations::exists);
    if (availableBackup.isEmpty()) {
      return Optional.empty();
    }

    Path backup = availableBackup.orElseThrow();
    try {
      fileOperations.move(backup, progress.destination());
      return Optional.empty();
    } catch (IOException restorationFailure) {
      failure.addSuppressed(restorationFailure);
      return Optional.of(backup);
    }
  }

  private Optional<Path> cleanStaging(PublicationProgress progress, IOException failure) {
    Optional<Path> availableStaging = progress.staging().filter(fileOperations::exists);
    if (availableStaging.isEmpty()) {
      return Optional.empty();
    }

    Path staging = availableStaging.orElseThrow();
    try {
      fileOperations.delete(staging);
      return Optional.empty();
    } catch (IOException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
      return Optional.of(staging).filter(fileOperations::exists);
    }
  }

  private record RecoveryOutcome(Optional<Path> unrestoredBackup, Optional<Path> residualStaging) {
    private String diagnostic() {
      String backupDiagnostic = unrestoredBackup
        .map(backup -> " Previous installation could not be restored. Backup remains at %s.".formatted(backup))
        .orElse("");
      String stagingDiagnostic = residualStaging.map(staging -> " Staging remains at %s.".formatted(staging)).orElse("");
      return backupDiagnostic + stagingDiagnostic;
    }
  }

  private record PublicationProgress(Path destination, Optional<Path> staging, Optional<Path> backup) {
    private static PublicationProgress start(Path destination) {
      return new PublicationProgress(destination, Optional.empty(), Optional.empty());
    }

    private PublicationProgress withStaging(Path newStaging) {
      return new PublicationProgress(destination, Optional.of(newStaging), backup);
    }

    private PublicationProgress withBackup(Path newBackup) {
      return new PublicationProgress(destination, staging, Optional.of(newBackup));
    }

    private PublicationProgress afterCommit() {
      return new PublicationProgress(destination, Optional.empty(), backup);
    }

    private Path requiredStaging() {
      return staging.orElseThrow();
    }
  }

  private static final class PostCommitCleanupException extends AgentSkillInstallationException {

    private PostCommitCleanupException(String message, IOException cause) {
      super(message, cause);
    }
  }
}
