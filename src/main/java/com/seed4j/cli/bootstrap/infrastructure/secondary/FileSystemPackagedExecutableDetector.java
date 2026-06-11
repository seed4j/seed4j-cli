package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.PackagedExecutableDetector;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystemPackagedExecutableDetector implements PackagedExecutableDetector {

  @Override
  public boolean packagedExecutable(Path executablePath) {
    return Files.isRegularFile(executablePath) && executablePath.getFileName().toString().endsWith(".jar");
  }
}
