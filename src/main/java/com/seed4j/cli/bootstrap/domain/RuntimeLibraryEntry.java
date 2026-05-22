package com.seed4j.cli.bootstrap.domain;

import java.util.Optional;

record RuntimeLibraryEntry(String fileName, Optional<RuntimeLibraryIdentity> identity) {
  static RuntimeLibraryEntry fromFileName(String fileName) {
    return new RuntimeLibraryEntry(fileName, RuntimeLibraryIdentity.fromJarFileName(fileName));
  }
}
