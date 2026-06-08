package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallationPort;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Service;

@Service
public class RuntimeExtensionInstallApplicationService {

  private final RuntimeExtensionInstallationPort runtimeExtensionInstallationPort;

  public RuntimeExtensionInstallApplicationService(RuntimeExtensionInstallationPort runtimeExtensionInstallationPort) {
    Assert.notNull("runtimeExtensionInstallationPort", runtimeExtensionInstallationPort);

    this.runtimeExtensionInstallationPort = runtimeExtensionInstallationPort;
  }

  public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
    Assert.notNull("request", request);

    return runtimeExtensionInstallationPort.install(request);
  }
}
