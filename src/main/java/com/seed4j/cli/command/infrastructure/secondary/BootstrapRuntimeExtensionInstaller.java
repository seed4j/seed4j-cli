package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionInstaller;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionInstaller.JavaRuntimeExtensionInstallation;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionInstaller.JavaRuntimeExtensionInstallationException;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionInstaller.JavaRuntimeExtensionInstallationRequest;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallationException;
import com.seed4j.cli.command.domain.RuntimeExtensionInstalledJarPath;
import com.seed4j.cli.command.domain.RuntimeExtensionInstaller;
import com.seed4j.cli.command.domain.RuntimeExtensionMetadataPath;
import com.seed4j.cli.command.domain.RuntimeExtensionReplacementStatus;
import com.seed4j.cli.command.domain.RuntimeModeConfigurationPath;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Component;

@Component
public class BootstrapRuntimeExtensionInstaller implements RuntimeExtensionInstaller {

  private final JavaRuntimeExtensionInstaller runtimeExtensionInstaller;

  public BootstrapRuntimeExtensionInstaller(JavaRuntimeExtensionInstaller runtimeExtensionInstaller) {
    Assert.notNull("runtimeExtensionInstaller", runtimeExtensionInstaller);

    this.runtimeExtensionInstaller = runtimeExtensionInstaller;
  }

  @Override
  public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
    Assert.notNull("request", request);

    try {
      JavaRuntimeExtensionInstallation installation = runtimeExtensionInstaller.install(
        new JavaRuntimeExtensionInstallationRequest(
          request.extensionJarPath().value(),
          request.distributionId().value(),
          request.distributionVersion().value()
        )
      );

      return new RuntimeExtensionInstallResult(
        new RuntimeExtensionInstalledJarPath(installation.extensionJarPath()),
        new RuntimeExtensionMetadataPath(installation.metadataPath()),
        new RuntimeModeConfigurationPath(installation.configPath()),
        RuntimeExtensionReplacementStatus.from(installation.runtimeReplaced())
      );
    } catch (JavaRuntimeExtensionInstallationException exception) {
      throw new RuntimeExtensionInstallationException(exception.getMessage(), exception);
    }
  }
}
