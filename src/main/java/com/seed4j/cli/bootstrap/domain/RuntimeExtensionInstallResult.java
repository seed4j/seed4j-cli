package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

public record RuntimeExtensionInstallResult(Path extensionJarPath, Path metadataPath, Path configPath, boolean runtimeReplaced) {}
