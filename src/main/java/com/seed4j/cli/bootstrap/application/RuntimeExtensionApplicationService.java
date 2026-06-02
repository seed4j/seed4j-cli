package com.seed4j.cli.bootstrap.application;

import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstaller;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Service;

@Service
public class RuntimeExtensionApplicationService {

  private final RuntimeExtensionInstaller runtimeExtensionInstaller;

  public RuntimeExtensionApplicationService(RuntimeExtensionInstaller runtimeExtensionInstaller) {
    Assert.notNull("runtimeExtensionInstaller", runtimeExtensionInstaller);

    this.runtimeExtensionInstaller = runtimeExtensionInstaller;
  }

  public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
    Assert.notNull("request", request);
    return runtimeExtensionInstaller.install(request);
  }
}
