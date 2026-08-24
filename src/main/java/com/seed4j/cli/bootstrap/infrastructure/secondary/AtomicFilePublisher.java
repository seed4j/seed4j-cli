package com.seed4j.cli.bootstrap.infrastructure.secondary;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

class AtomicFilePublisher {

  void publishContent(String content, Path targetPath) throws IOException {
    publish(temporaryPath -> Files.writeString(temporaryPath, content), targetPath);
  }

  void publishSource(Path sourcePath, Path targetPath) throws IOException {
    publish(temporaryPath -> Files.copy(sourcePath, temporaryPath, StandardCopyOption.REPLACE_EXISTING), targetPath);
  }

  private void publish(TemporaryFileWriter temporaryFileWriter, Path targetPath) throws IOException {
    Path temporaryPath = temporaryPath(targetPath);
    try {
      temporaryFileWriter.write(temporaryPath);
      moveReplacing(temporaryPath, targetPath);
    } catch (IOException ioException) {
      Files.deleteIfExists(temporaryPath);
      throw ioException;
    }
  }

  private void moveReplacing(Path sourcePath, Path targetPath) throws IOException {
    try {
      Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException _) {
      Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private Path temporaryPath(Path targetPath) {
    String temporaryFileName = "." + targetPath.getFileName() + ".tmp-" + UUID.randomUUID();
    return targetPath.getParent().resolve(temporaryFileName);
  }

  @FunctionalInterface
  private interface TemporaryFileWriter {
    void write(Path temporaryPath) throws IOException;
  }
}
