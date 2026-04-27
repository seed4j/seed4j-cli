package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

class RuntimeExtensionLoaderPathResolver {

  String resolve(Path extensionJarPath) {
    String extensionJarUri = extensionJarPath.toUri().toString();
    String bootInfClassesLocation = "jar:" + extensionJarUri + "!/BOOT-INF/classes";
    String bootInfLibLocation = "jar:" + extensionJarUri + "!/BOOT-INF/lib/";
    return bootInfClassesLocation + "," + bootInfLibLocation;
  }
}
