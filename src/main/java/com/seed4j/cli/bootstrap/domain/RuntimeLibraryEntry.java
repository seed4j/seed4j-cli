package com.seed4j.cli.bootstrap.domain;

import java.util.Optional;

public record RuntimeLibraryEntry(RuntimeLibraryFileName fileName, Optional<RuntimeLibraryIdentity> identity) {
  public static RuntimeLibraryEntry fromFileName(String fileName) {
    return new RuntimeLibraryEntry(new RuntimeLibraryFileName(fileName), RuntimeLibraryIdentity.fromJarFileName(fileName));
  }
}
