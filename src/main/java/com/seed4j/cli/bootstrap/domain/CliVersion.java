package com.seed4j.cli.bootstrap.domain;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.NonNull;

record CliVersion(String value, String normalizedValue, List<Integer> segments) implements Comparable<CliVersion> {
  private static final String COMPATIBILITY_FIELD_NAME = "compatibility.min-cli-version";

  static CliVersion current(String value) {
    return from(value, "Invalid current CLI version: " + value);
  }

  static CliVersion minimumCompatibility(String value) {
    return from(value, "Invalid " + COMPATIBILITY_FIELD_NAME + ": " + value);
  }

  void validateCompatibilityWith(CliVersion minimumCompatibleVersion) {
    if (atLeast(minimumCompatibleVersion)) {
      return;
    }

    throw new InvalidRuntimeConfigurationException(
      "Invalid "
        + COMPATIBILITY_FIELD_NAME
        + ", expected minimum "
        + minimumCompatibleVersion
        + " to be compatible with current CLI version "
        + this
        + " (normalized as "
        + normalizedValue
        + ")"
    );
  }

  private static CliVersion from(String value, String invalidMessage) {
    try {
      String normalizedValue = normalized(value);
      List<Integer> segments = Arrays.stream(normalizedValue.split("\\.")).map(CliVersion::parsedSegment).toList();

      return new CliVersion(value, normalizedValue, segments);
    } catch (NumberFormatException e) {
      throw new InvalidRuntimeConfigurationException(invalidMessage);
    }
  }

  boolean atLeast(CliVersion other) {
    return compareTo(other) >= 0;
  }

  @Override
  public int compareTo(CliVersion other) {
    int segmentCount = Math.max(segments.size(), other.segments.size());

    for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
      int currentSegment = versionSegment(segments, segmentIndex);
      int otherSegment = versionSegment(other.segments, segmentIndex);

      if (currentSegment != otherSegment) {
        return Integer.compare(currentSegment, otherSegment);
      }
    }

    return 0;
  }

  private static int versionSegment(List<Integer> segments, int index) {
    if (index >= segments.size()) {
      return 0;
    }

    return segments.get(index);
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

  private static Integer parsedSegment(String segment) {
    return Integer.valueOf(segment);
  }
}
