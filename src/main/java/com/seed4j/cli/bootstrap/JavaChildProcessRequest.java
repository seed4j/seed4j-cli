package com.seed4j.cli.bootstrap;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

record JavaChildProcessRequest(
  Path executableJar,
  String mainClass,
  Map<String, String> systemProperties,
  List<String> arguments,
  RuntimeSelection runtimeSelection
) {}
