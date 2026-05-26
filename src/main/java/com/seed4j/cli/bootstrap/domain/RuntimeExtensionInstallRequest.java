package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

public record RuntimeExtensionInstallRequest(Path extensionJarPath, String distributionId, String distributionVersion) {}
