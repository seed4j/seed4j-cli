package com.seed4j.cli.bootstrap;

import java.util.Arrays;
import org.jspecify.annotations.NonNull;

record CliVersion(String value) implements Comparable<CliVersion> {
  static CliVersion from(String value) {
    validate(value);
    return new CliVersion(value);
  }

  boolean atLeast(CliVersion other) {
    return compareTo(other) >= 0;
  }

  String normalizedValue() {
    int qualifierIndex = value.indexOf('-');
    if (qualifierIndex < 0) {
      return value;
    }

    return value.substring(0, qualifierIndex);
  }

  @Override
  public int compareTo(CliVersion other) {
    String[] currentSegments = normalizedValue().split("\\.");
    String[] otherSegments = other.normalizedValue().split("\\.");
    int segmentCount = Math.max(currentSegments.length, otherSegments.length);

    for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
      int currentSegment = versionSegment(currentSegments, segmentIndex);
      int otherSegment = versionSegment(otherSegments, segmentIndex);

      if (currentSegment != otherSegment) {
        return Integer.compare(currentSegment, otherSegment);
      }
    }

    return 0;
  }

  private static int versionSegment(String[] segments, int index) {
    if (index >= segments.length) {
      return 0;
    }

    return parsedSegment(segments[index]);
  }

  private static void validate(String value) {
    String[] segments = normalized(value).split("\\.");
    Arrays.stream(segments).forEach(CliVersion::parsedSegment);
  }

  private static String normalized(String value) {
    int qualifierIndex = value.indexOf('-');
    if (qualifierIndex < 0) {
      return value;
    }

    return value.substring(0, qualifierIndex);
  }

  @Override
  public @NonNull String toString() {
    return value;
  }

  private static int parsedSegment(String segment) {
    return Integer.parseInt(segment);
  }
}
