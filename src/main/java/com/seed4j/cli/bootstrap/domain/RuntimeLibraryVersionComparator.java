package com.seed4j.cli.bootstrap.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

final class RuntimeLibraryVersionComparator {

  private static final Pattern NUMERIC_VERSION_PATTERN = Pattern.compile("^\\d++(?:\\.\\d++)*+$");
  private static final Pattern QUALIFIED_NUMERIC_VERSION_PATTERN = Pattern.compile("^(\\d++(?:\\.\\d++)*+)[.-]([A-Za-z]++)$");

  RuntimeLibraryVersionComparison compare(String extensionVersion, String cliVersion) {
    if (normalizedVersion(extensionVersion).equals(normalizedVersion(cliVersion))) {
      return RuntimeLibraryVersionComparison.SAME_VERSION;
    }

    Optional<RuntimeLibraryVersionComparison> numericComparison = numericVersionComparison(extensionVersion, cliVersion);
    if (numericComparison.isPresent()) {
      return numericComparison.get();
    }

    Optional<RuntimeLibraryVersionComparison> qualifiedNumericComparison = qualifiedNumericVersionComparison(extensionVersion, cliVersion);

    return qualifiedNumericComparison.orElse(RuntimeLibraryVersionComparison.UNCOMPARABLE);
  }

  private Optional<RuntimeLibraryVersionComparison> numericVersionComparison(String extensionVersion, String cliVersion) {
    Optional<List<Integer>> extensionVersionSegments = parsedVersionSegments(extensionVersion);
    Optional<List<Integer>> cliVersionSegments = parsedVersionSegments(cliVersion);
    if (extensionVersionSegments.isPresent() && cliVersionSegments.isPresent()) {
      return Optional.of(versionComparison(extensionVersionSegments.get(), cliVersionSegments.get()));
    }

    return Optional.empty();
  }

  private Optional<RuntimeLibraryVersionComparison> qualifiedNumericVersionComparison(String extensionVersion, String cliVersion) {
    Optional<QualifiedNumericVersion> extensionQualifiedVersion = qualifiedNumericVersion(extensionVersion);
    Optional<QualifiedNumericVersion> cliQualifiedVersion = qualifiedNumericVersion(cliVersion);
    if (extensionQualifiedVersion.isEmpty() || cliQualifiedVersion.isEmpty()) {
      return Optional.empty();
    }

    if (!extensionQualifiedVersion.get().qualifier().equals(cliQualifiedVersion.get().qualifier())) {
      return Optional.empty();
    }

    return Optional.of(versionComparison(extensionQualifiedVersion.get().numericSegments(), cliQualifiedVersion.get().numericSegments()));
  }

  private RuntimeLibraryVersionComparison versionComparison(List<Integer> extensionVersionSegments, List<Integer> cliVersionSegments) {
    int comparison = compareVersionSegments(extensionVersionSegments, cliVersionSegments);
    if (comparison < 0) {
      return RuntimeLibraryVersionComparison.EXTENSION_OLDER;
    }

    if (comparison > 0) {
      return RuntimeLibraryVersionComparison.EXTENSION_NEWER;
    }

    return RuntimeLibraryVersionComparison.SAME_VERSION;
  }

  private Optional<QualifiedNumericVersion> qualifiedNumericVersion(String version) {
    String normalizedVersion = normalizedVersion(version);
    java.util.regex.Matcher matcher = QUALIFIED_NUMERIC_VERSION_PATTERN.matcher(normalizedVersion);
    if (!matcher.matches()) {
      return Optional.empty();
    }

    Optional<List<Integer>> numericSegments = parsedVersionSegments(matcher.group(1));
    if (numericSegments.isEmpty()) {
      return Optional.empty();
    }

    String qualifier = matcher.group(2).toLowerCase(Locale.ROOT);
    return Optional.of(new QualifiedNumericVersion(numericSegments.get(), qualifier));
  }

  private int compareVersionSegments(List<Integer> leftSegments, List<Integer> rightSegments) {
    int segmentCount = Math.max(leftSegments.size(), rightSegments.size());
    for (int index = 0; index < segmentCount; index++) {
      int leftSegment = versionSegment(leftSegments, index);
      int rightSegment = versionSegment(rightSegments, index);
      if (leftSegment != rightSegment) {
        return Integer.compare(leftSegment, rightSegment);
      }
    }

    return 0;
  }

  private int versionSegment(List<Integer> segments, int index) {
    if (index >= segments.size()) {
      return 0;
    }

    return segments.get(index);
  }

  private Optional<List<Integer>> parsedVersionSegments(String version) {
    String normalizedVersion = normalizedVersion(version);
    if (!numericVersion(normalizedVersion)) {
      return Optional.empty();
    }

    try {
      List<Integer> segments = Arrays.stream(normalizedVersion.split("\\.")).map(Integer::parseInt).toList();
      return Optional.of(segments);
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  private String normalizedVersion(String version) {
    if (version.startsWith("v") || version.startsWith("V")) {
      return version.substring(1);
    }

    return version;
  }

  private boolean numericVersion(String version) {
    return NUMERIC_VERSION_PATTERN.matcher(version).matches();
  }

  enum RuntimeLibraryVersionComparison {
    EXTENSION_OLDER,
    EXTENSION_NEWER,
    SAME_VERSION,
    UNCOMPARABLE,
  }

  private record QualifiedNumericVersion(List<Integer> numericSegments, String qualifier) {}
}
