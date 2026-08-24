package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsInstallation;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionMetadataPath;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.shared.error.domain.Assert;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSystemRuntimeExtensionArtifactsRepository implements RuntimeExtensionArtifactsRepository {

  private final Seed4JCliHome cliHome;
  private final AtomicFilePublisher filePublisher;

  public FileSystemRuntimeExtensionArtifactsRepository(Seed4JCliHome cliHome) {
    Assert.notNull("cliHome", cliHome);

    this.cliHome = cliHome;
    filePublisher = new AtomicFilePublisher();
  }

  @Override
  public boolean activeRuntimePresent() {
    return Files.exists(extensionJarPath()) || Files.exists(metadataPath());
  }

  @Override
  public RuntimeExtensionArtifactsInstallation install(RuntimeExtensionInstallRequest request) {
    try {
      Path runtimeDirectoryPath = extensionJarPath().getParent();
      Files.createDirectories(runtimeDirectoryPath);
      filePublisher.publishSource(request.extensionJarPath().path(), extensionJarPath());
      filePublisher.publishContent(metadataContent(request), metadataPath());
      return new RuntimeExtensionArtifactsInstallation(
        new RuntimeExtensionJarPath(extensionJarPath()),
        new RuntimeExtensionMetadataPath(metadataPath())
      );
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError("Could not install runtime extension.", ioException);
    }
  }

  private Path extensionJarPath() {
    return cliHome.path().resolve(".config/seed4j-cli/runtime/active/extension.jar");
  }

  private Path metadataPath() {
    return cliHome.path().resolve(".config/seed4j-cli/runtime/active/metadata.yml");
  }

  private static String metadataContent(RuntimeExtensionInstallRequest request) {
    return """
    distribution:
      id: %s
      version: %s
    """.formatted(request.distributionId().id(), request.distributionVersion().version());
  }
}
