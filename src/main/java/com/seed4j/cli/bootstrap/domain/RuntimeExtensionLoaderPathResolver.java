package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

class RuntimeExtensionLoaderPathResolver {

  String resolve(Path overlayClassesPath) {
    return overlayClassesPath.toString();
  }
}
