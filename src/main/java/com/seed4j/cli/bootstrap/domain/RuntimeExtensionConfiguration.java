package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

public record RuntimeExtensionConfiguration(Path jarPath, Path metadataPath) {}
