package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionConfiguration;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class FileSystemRuntimeExtensionArtifactsRepository implements RuntimeExtensionArtifactsRepository {

  @Override
  public boolean activeRuntimePresent(RuntimeExtensionConfiguration runtimeExtensionConfiguration) {
    return (Files.exists(runtimeExtensionConfiguration.jarPath()) || Files.exists(runtimeExtensionConfiguration.metadataPath()));
  }

  @Override
  public void install(RuntimeExtensionInstallRequest request, RuntimeExtensionConfiguration runtimeExtensionConfiguration)
    throws IOException {
    Path runtimeDirectoryPath = runtimeExtensionConfiguration.jarPath().getParent();
    Files.createDirectories(runtimeDirectoryPath);
    replacePathWithSource(request.extensionJarPath().filePath(), runtimeExtensionConfiguration.jarPath());
    replacePathWithContent(metadataContent(request), runtimeExtensionConfiguration.metadataPath());
  }

  private static String metadataContent(RuntimeExtensionInstallRequest request) {
    return """
    distribution:
      id: %s
      version: %s
    """.formatted(request.distributionId().id(), request.distributionVersion().version());
  }

  private static void replacePathWithSource(Path sourcePath, Path targetPath) throws IOException {
    Path temporaryPath = temporaryPath(targetPath);
    try {
      Files.copy(sourcePath, temporaryPath, StandardCopyOption.REPLACE_EXISTING);
      moveReplacing(temporaryPath, targetPath);
    } catch (IOException ioException) {
      Files.deleteIfExists(temporaryPath);
      throw ioException;
    }
  }

  private static void replacePathWithContent(String content, Path targetPath) throws IOException {
    Path temporaryPath = temporaryPath(targetPath);
    try {
      Files.writeString(temporaryPath, content);
      moveReplacing(temporaryPath, targetPath);
    } catch (IOException ioException) {
      Files.deleteIfExists(temporaryPath);
      throw ioException;
    }
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Cache publication race branch depends on filesystem concurrency timing")
  private static void moveReplacing(Path sourcePath, Path targetPath) throws IOException {
    try {
      Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException _) {
      Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static Path temporaryPath(Path targetPath) {
    String temporaryFileName = "." + targetPath.getFileName() + ".tmp-" + UUID.randomUUID();
    return targetPath.getParent().resolve(temporaryFileName);
  }
}
