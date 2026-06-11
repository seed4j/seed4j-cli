package com.seed4j.cli.bootstrap.domain;

public interface RuntimeExtensionArtifactsRepository {
  boolean activeRuntimePresent();

  RuntimeExtensionArtifactsInstallation install(RuntimeExtensionInstallRequest request);
}
