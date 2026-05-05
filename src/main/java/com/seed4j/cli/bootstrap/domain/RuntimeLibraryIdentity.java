package com.seed4j.cli.bootstrap.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record RuntimeLibraryIdentity(String coordinate, String version) {
  private static final Pattern LIBRARY_COORDINATE_AND_VERSION_PATTERN = Pattern.compile("^(.+)-([0-9].*)\\.jar$");

  static Optional<RuntimeLibraryIdentity> fromJarFileName(String libraryFileName) {
    Matcher matcher = LIBRARY_COORDINATE_AND_VERSION_PATTERN.matcher(libraryFileName);
    if (!matcher.matches()) {
      return Optional.empty();
    }

    String coordinate = matcher.group(1);
    String version = matcher.group(2);
    return Optional.of(new RuntimeLibraryIdentity(coordinate, version));
  }
}
