package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;

public interface RuntimeExtensionArtifactsRepository {
  boolean activeRuntimePresent(RuntimeExtensionConfiguration runtimeExtensionConfiguration);

  void install(RuntimeExtensionInstallRequest request, RuntimeExtensionConfiguration runtimeExtensionConfiguration) throws IOException;
}
