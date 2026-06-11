package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.command.domain.RuntimeExtensionInstaller;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Service;

@Service
public class RuntimeExtensionInstallApplicationService {

  private final RuntimeExtensionInstaller runtimeExtensionInstaller;

  public RuntimeExtensionInstallApplicationService(RuntimeExtensionInstaller runtimeExtensionInstaller) {
    Assert.notNull("runtimeExtensionInstaller", runtimeExtensionInstaller);

    this.runtimeExtensionInstaller = runtimeExtensionInstaller;
  }

  public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
    Assert.notNull("request", request);

    return runtimeExtensionInstaller.install(request);
  }
}
