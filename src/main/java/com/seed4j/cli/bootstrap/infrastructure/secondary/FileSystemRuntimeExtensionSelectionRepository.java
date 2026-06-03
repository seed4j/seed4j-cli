package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionMetadata;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionPackageValidator;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionSelectionRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSystemRuntimeExtensionSelectionRepository implements RuntimeExtensionSelectionRepository {

  private final Seed4JCliHome cliHome;
  private final RuntimeExtensionPackageValidator runtimeExtensionPackageValidator;
  private final RuntimeExtensionMetadataReader runtimeExtensionMetadataReader;

  public FileSystemRuntimeExtensionSelectionRepository(
    Seed4JCliHome cliHome,
    RuntimeExtensionPackageValidator runtimeExtensionPackageValidator
  ) {
    Assert.notNull("cliHome", cliHome);
    Assert.notNull("runtimeExtensionPackageValidator", runtimeExtensionPackageValidator);

    this.cliHome = cliHome;
    this.runtimeExtensionPackageValidator = runtimeExtensionPackageValidator;
    this.runtimeExtensionMetadataReader = new RuntimeExtensionMetadataReader();
  }

  @Override
  public RuntimeSelection activeRuntimeSelection() {
    Path metadataPath = metadataPath();
    Path extensionJarPath = extensionJarPath();
    if (!Files.exists(metadataPath)) {
      throw new InvalidRuntimeConfigurationException("Invalid runtime metadata file: " + metadataPath);
    }

    if (!Files.exists(extensionJarPath)) {
      throw new InvalidRuntimeConfigurationException("Invalid runtime jar file: " + extensionJarPath);
    }

    RuntimeExtensionJarPath runtimeExtensionJarPath = new RuntimeExtensionJarPath(extensionJarPath);
    runtimeExtensionPackageValidator.validate(runtimeExtensionJarPath);
    RuntimeExtensionMetadata metadata = runtimeExtensionMetadataReader.read(metadataPath);

    return RuntimeSelection.extension(runtimeExtensionJarPath, metadata.distributionId(), metadata.distributionVersion());
  }

  private Path extensionJarPath() {
    return cliHome.path().resolve(".config/seed4j-cli/runtime/active/extension.jar");
  }

  private Path metadataPath() {
    return cliHome.path().resolve(".config/seed4j-cli/runtime/active/metadata.yml");
  }
}
