package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class FileSystemPackagedExecutableDetectorTest {

  @Test
  void shouldDetectRegularJarFileAsPackagedExecutable() throws IOException {
    Path executablePath = Files.createTempFile("seed4j-cli-", ".jar");
    FileSystemPackagedExecutableDetector detector = new FileSystemPackagedExecutableDetector();

    boolean packagedExecutable = detector.packagedExecutable(executablePath);

    assertThat(packagedExecutable).isTrue();
  }

  @Test
  void shouldNotDetectDirectoryAsPackagedExecutable() throws IOException {
    Path executablePath = Files.createTempDirectory("seed4j-cli-");
    FileSystemPackagedExecutableDetector detector = new FileSystemPackagedExecutableDetector();

    boolean packagedExecutable = detector.packagedExecutable(executablePath);

    assertThat(packagedExecutable).isFalse();
  }

  @Test
  void shouldNotDetectRegularNonJarFileAsPackagedExecutable() throws IOException {
    Path executablePath = Files.createTempFile("seed4j-cli-", ".txt");
    FileSystemPackagedExecutableDetector detector = new FileSystemPackagedExecutableDetector();

    boolean packagedExecutable = detector.packagedExecutable(executablePath);

    assertThat(packagedExecutable).isFalse();
  }
}
