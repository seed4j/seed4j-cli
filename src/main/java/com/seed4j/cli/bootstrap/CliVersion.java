package com.seed4j.cli.bootstrap;

record CliVersion(String value) implements Comparable<CliVersion> {
  static CliVersion from(String value) {
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

    return Integer.parseInt(segments[index]);
  }

  @Override
  public String toString() {
    return value;
  }
}
