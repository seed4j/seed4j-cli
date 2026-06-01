package com.seed4j.cli.bootstrap.application;

import java.nio.file.Path;

@FunctionalInterface
public interface PreSpringLauncherFactory {
  PreSpringLauncher create(Path userHomePath, Path executablePath, Path javaExecutablePath);
}
