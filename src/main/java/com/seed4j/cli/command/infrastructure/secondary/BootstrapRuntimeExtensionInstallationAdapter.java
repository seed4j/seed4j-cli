package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionInstaller;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionInstaller.JavaRuntimeExtensionInstallation;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionInstaller.JavaRuntimeExtensionInstallationException;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeExtensionInstaller.JavaRuntimeExtensionInstallationRequest;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallationException;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallationPort;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Component;

@Component
public class BootstrapRuntimeExtensionInstallationAdapter implements RuntimeExtensionInstallationPort {

  private final JavaRuntimeExtensionInstaller runtimeExtensionInstaller;

  public BootstrapRuntimeExtensionInstallationAdapter(JavaRuntimeExtensionInstaller runtimeExtensionInstaller) {
    Assert.notNull("runtimeExtensionInstaller", runtimeExtensionInstaller);

    this.runtimeExtensionInstaller = runtimeExtensionInstaller;
  }

  @Override
  public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
    Assert.notNull("request", request);

    try {
      JavaRuntimeExtensionInstallation installation = runtimeExtensionInstaller.install(
        new JavaRuntimeExtensionInstallationRequest(request.extensionJarPath(), request.distributionId(), request.distributionVersion())
      );

      return new RuntimeExtensionInstallResult(
        installation.extensionJarPath(),
        installation.metadataPath(),
        installation.configPath(),
        installation.runtimeReplaced()
      );
    } catch (JavaRuntimeExtensionInstallationException exception) {
      throw new RuntimeExtensionInstallationException(exception.getMessage(), exception);
    }
  }
}
