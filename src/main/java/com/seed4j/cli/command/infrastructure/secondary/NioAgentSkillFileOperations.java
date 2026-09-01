package com.seed4j.cli.command.infrastructure.secondary;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.springframework.stereotype.Component;

@Component
class NioAgentSkillFileOperations implements AgentSkillFileOperations {

  @Override
  public void createDirectories(Path directory) throws IOException {
    Files.createDirectories(directory);
  }

  @Override
  public Path createTemporaryDirectory(Path directory, String prefix) throws IOException {
    return Files.createTempDirectory(directory, prefix);
  }

  @Override
  public void write(Path path, byte[] content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.write(path, content);
  }

  @Override
  public boolean exists(Path path) {
    return Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS);
  }

  @Override
  public void move(Path source, Path destination) throws IOException {
    Files.move(source, destination);
  }

  @Override
  public void delete(Path path) throws IOException {
    Files.walkFileTree(
      path,
      new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
          FileVisitResult result = super.postVisitDirectory(directory, exception);
          Files.delete(directory);
          return result;
        }
      }
    );
  }
}
