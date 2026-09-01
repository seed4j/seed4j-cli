package com.seed4j.cli.command.infrastructure.secondary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

final class AgentSkillInstallationFailures {

  private AgentSkillInstallationFailures() {}

  static AgentSkillFileOperations stagingWrite() {
    return new WriteFailingFileOperations();
  }

  static AgentSkillFileOperations stagingPreparation() {
    return new TemporaryDirectoryFailingFileOperations();
  }

  static AgentSkillFileOperations publication() {
    return new MoveFailingFileOperations(Set.of(2));
  }

  static AgentSkillFileOperations publicationAndRestoration() {
    return new MoveFailingFileOperations(Set.of(2, 3));
  }

  static AgentSkillFileOperations publicationAndStagingCleanup() {
    return new PublicationAndStagingCleanupFailingFileOperations();
  }

  static AgentSkillFileOperations backupCleanup() {
    return new DeleteFailingFileOperations();
  }

  static AgentSkillFileOperations removedBackupCleanup() {
    return new DeleteThenFailingFileOperations();
  }

  private static final class WriteFailingFileOperations extends NioAgentSkillFileOperations {

    @Override
    public void write(Path path, byte[] content) throws IOException {
      throw new IOException("staging denied");
    }
  }

  private static final class TemporaryDirectoryFailingFileOperations extends NioAgentSkillFileOperations {

    @Override
    public Path createTemporaryDirectory(Path directory, String prefix) throws IOException {
      throw new IOException("staging directory denied");
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

  private static final class DeleteThenFailingFileOperations extends NioAgentSkillFileOperations {

    @Override
    public void delete(Path path) throws IOException {
      super.delete(path);
      throw new IOException("cleanup failed after deletion");
    }
  }

  private static final class PublicationAndStagingCleanupFailingFileOperations extends NioAgentSkillFileOperations {

    private int moveCount;

    @Override
    public void move(Path source, Path destination) throws IOException {
      moveCount++;
      if (moveCount == 2) {
        throw new IOException("publication denied");
      }
      super.move(source, destination);
    }

    @Override
    public void delete(Path path) throws IOException {
      throw new IOException("staging cleanup denied");
    }
  }
}
