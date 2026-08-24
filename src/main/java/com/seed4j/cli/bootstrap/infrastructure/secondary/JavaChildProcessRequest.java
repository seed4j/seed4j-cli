package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.Seed4JCliExecutablePath;
import java.util.List;
import java.util.Map;

record JavaChildProcessRequest(
  Seed4JCliExecutablePath executableJar,
  String mainClass,
  Map<String, String> systemProperties,
  List<String> arguments
) {
  JavaChildProcessRequest {
    systemProperties = Map.copyOf(systemProperties);
    arguments = List.copyOf(arguments);
  }
}
