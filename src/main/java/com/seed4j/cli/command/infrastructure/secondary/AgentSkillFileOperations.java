package com.seed4j.cli.command.infrastructure.secondary;

import java.io.IOException;
import java.nio.file.Path;

interface AgentSkillFileOperations {
  void createDirectories(Path directory) throws IOException;

  Path createTemporaryDirectory(Path directory, String prefix) throws IOException;

  void write(Path path, byte[] content) throws IOException;

  boolean exists(Path path);

  void move(Path source, Path destination) throws IOException;

  void delete(Path path) throws IOException;
}
