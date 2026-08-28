package com.seed4j.cli.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPathStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class NioModuleSetProjectPathValidatorTest {

  @Test
  void shouldAcceptExistingAccessibleDirectory(@TempDir Path projectPath) {
    NioModuleSetProjectPathValidator validator = new NioModuleSetProjectPathValidator();

    ModuleSetProjectPathStatus status = validator.validate(new ModuleSetProjectPath(projectPath));

    assertThat(status).isEqualTo(ModuleSetProjectPathStatus.VALID);
  }

  @ParameterizedTest
  @ValueSource(strings = { "rw-------", "r-x------" })
  void shouldRejectExistingDirectoryWithoutExecutionOrWriting(String permissions, @TempDir Path projectPath) throws IOException {
    assumeTrue(Files.getFileStore(projectPath).supportsFileAttributeView(PosixFileAttributeView.class));
    NioModuleSetProjectPathValidator validator = new NioModuleSetProjectPathValidator();
    Files.setPosixFilePermissions(projectPath, PosixFilePermissions.fromString(permissions));
    ModuleSetProjectPathStatus status;

    try {
      status = validator.validate(new ModuleSetProjectPath(projectPath));
    } finally {
      Files.setPosixFilePermissions(projectPath, PosixFilePermissions.fromString("rwx------"));
    }

    assertThat(status).isEqualTo(ModuleSetProjectPathStatus.NOT_ACCESSIBLE);
  }

  @Test
  void shouldRejectExistingFile(@TempDir Path parent) throws IOException {
    Path projectPath = Files.writeString(parent.resolve("project.txt"), "not a directory");
    NioModuleSetProjectPathValidator validator = new NioModuleSetProjectPathValidator();

    ModuleSetProjectPathStatus status = validator.validate(new ModuleSetProjectPath(projectPath));

    assertThat(status).isEqualTo(ModuleSetProjectPathStatus.NOT_DIRECTORY);
  }

  @Test
  void shouldRejectBrokenSymbolicLinkDestinationWithoutCreatingItsTarget(@TempDir Path parent) throws IOException {
    Path missingTarget = parent.resolve("missing-target");
    Path projectPath = Files.createSymbolicLink(parent.resolve("project"), missingTarget);
    NioModuleSetProjectPathValidator validator = new NioModuleSetProjectPathValidator();

    ModuleSetProjectPathStatus status = validator.validate(new ModuleSetProjectPath(projectPath));

    assertThat(status).isEqualTo(ModuleSetProjectPathStatus.NOT_DIRECTORY);
    assertThat(Files.isSymbolicLink(projectPath)).isTrue();
    assertThat(missingTarget).doesNotExist();
  }

  @Test
  void shouldAcceptSymbolicLinkToAccessibleDirectory(@TempDir Path parent) throws IOException {
    Path target = Files.createDirectory(parent.resolve("target"));
    Path projectPath = Files.createSymbolicLink(parent.resolve("project"), target);
    NioModuleSetProjectPathValidator validator = new NioModuleSetProjectPathValidator();

    ModuleSetProjectPathStatus status = validator.validate(new ModuleSetProjectPath(projectPath));

    assertThat(status).isEqualTo(ModuleSetProjectPathStatus.VALID);
    assertThat(Files.isSymbolicLink(projectPath)).isTrue();
    assertThat(target).isEmptyDirectory();
  }

  @Test
  void shouldAcceptApparentlyCreatableDestinationWithoutCreatingIt(@TempDir Path parent) {
    Path projectPath = parent.resolve("first").resolve("second");
    NioModuleSetProjectPathValidator validator = new NioModuleSetProjectPathValidator();

    ModuleSetProjectPathStatus status = validator.validate(new ModuleSetProjectPath(projectPath));

    assertThat(status).isEqualTo(ModuleSetProjectPathStatus.VALID);
    assertThat(projectPath).doesNotExist();
  }

  @ParameterizedTest
  @ValueSource(strings = { "rw-------", "r-x------" })
  void shouldRejectAbsentDestinationBelowAncestorWithoutExecutionOrWriting(String permissions, @TempDir Path parent) throws IOException {
    assumeTrue(Files.getFileStore(parent).supportsFileAttributeView(PosixFileAttributeView.class));
    Path ancestor = Files.createDirectory(parent.resolve("ancestor"));
    Path projectPath = ancestor.resolve("project");
    NioModuleSetProjectPathValidator validator = new NioModuleSetProjectPathValidator();
    Files.setPosixFilePermissions(ancestor, PosixFilePermissions.fromString(permissions));
    ModuleSetProjectPathStatus status;

    try {
      status = validator.validate(new ModuleSetProjectPath(projectPath));
    } finally {
      Files.setPosixFilePermissions(ancestor, PosixFilePermissions.fromString("rwx------"));
    }

    assertThat(status).isEqualTo(ModuleSetProjectPathStatus.NOT_APPARENTLY_CREATABLE);
    assertThat(projectPath).doesNotExist();
  }

  @Test
  void shouldRejectDestinationBelowExistingFile(@TempDir Path parent) throws IOException {
    Path file = Files.writeString(parent.resolve("intermediate"), "not a directory");
    Path projectPath = file.resolve("project");
    NioModuleSetProjectPathValidator validator = new NioModuleSetProjectPathValidator();

    ModuleSetProjectPathStatus status = validator.validate(new ModuleSetProjectPath(projectPath));

    assertThat(status).isEqualTo(ModuleSetProjectPathStatus.NOT_APPARENTLY_CREATABLE);
    assertThat(projectPath).doesNotExist();
  }

  @Test
  void shouldRejectDestinationBelowBrokenSymbolicLinkWithoutCreatingAnything(@TempDir Path parent) throws IOException {
    Path missingTarget = parent.resolve("missing-target");
    Path intermediate = Files.createSymbolicLink(parent.resolve("intermediate"), missingTarget);
    Path projectPath = intermediate.resolve("project");
    NioModuleSetProjectPathValidator validator = new NioModuleSetProjectPathValidator();

    ModuleSetProjectPathStatus status = validator.validate(new ModuleSetProjectPath(projectPath));

    assertThat(status).isEqualTo(ModuleSetProjectPathStatus.NOT_APPARENTLY_CREATABLE);
    assertThat(Files.isSymbolicLink(intermediate)).isTrue();
    assertThat(missingTarget).doesNotExist();
    assertThat(projectPath).doesNotExist();
  }
}
