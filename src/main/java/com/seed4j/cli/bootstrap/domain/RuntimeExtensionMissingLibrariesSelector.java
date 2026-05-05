package com.seed4j.cli.bootstrap.domain;

import java.util.List;
import java.util.Set;

final class RuntimeExtensionMissingLibrariesSelector {

  List<String> select(List<String> extensionLibraries, Set<String> cliLibraries) {
    return extensionLibraries
      .stream()
      .filter(extensionLibrary -> !cliLibraries.contains(extensionLibrary))
      .toList();
  }
}
