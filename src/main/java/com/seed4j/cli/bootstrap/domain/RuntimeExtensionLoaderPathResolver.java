package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

@FunctionalInterface
public interface RuntimeExtensionLoaderPathResolver {
  String resolve(Path overlayClassesPath, Path extensionJarPath, Path executableJarPath);
}
