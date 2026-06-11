package com.seed4j.cli.bootstrap.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record RuntimeLibraryIdentity(RuntimeLibraryCoordinate coordinate, RuntimeLibraryVersion version) {
  private static final Pattern LIBRARY_COORDINATE_AND_VERSION_PATTERN = Pattern.compile(
    "^(.+)-(\\d[0-9A-Za-z.]*|v\\d[0-9A-Za-z.]*|RELEASE)\\.jar$"
  );

  public static Optional<RuntimeLibraryIdentity> fromJarFileName(String libraryFileName) {
    if (notJarFileName(libraryFileName)) {
      return Optional.empty();
    }

    if (notContainsCoordinateVersionDelimiter(libraryFileName)) {
      return Optional.empty();
    }

    Matcher matcher = LIBRARY_COORDINATE_AND_VERSION_PATTERN.matcher(libraryFileName);
    if (!matcher.matches()) {
      return Optional.empty();
    }

    RuntimeLibraryCoordinate coordinate = new RuntimeLibraryCoordinate(matcher.group(1));
    RuntimeLibraryVersion version = new RuntimeLibraryVersion(matcher.group(2));
    return Optional.of(new RuntimeLibraryIdentity(coordinate, version));
  }

  private static boolean notJarFileName(String libraryFileName) {
    return !libraryFileName.endsWith(".jar");
  }

  private static boolean notContainsCoordinateVersionDelimiter(String libraryFileName) {
    return libraryFileName.lastIndexOf('-') <= 0;
  }
}
