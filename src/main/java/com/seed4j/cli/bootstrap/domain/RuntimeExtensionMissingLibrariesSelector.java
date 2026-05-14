package com.seed4j.cli.bootstrap.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

final class RuntimeExtensionMissingLibrariesSelector {

  List<String> select(List<RuntimeLibraryEntry> extensionLibraries, Set<RuntimeLibraryEntry> cliLibraries) {
    Set<String> cliLibraryFileNames = cliLibraryFileNames(cliLibraries);
    Map<String, String> cliLibraryVersionsByCoordinate = libraryVersionsByCoordinate(cliLibraries);

    return extensionLibraries
      .stream()
      .map(extensionLibrary -> decisionFor(extensionLibrary, cliLibraryFileNames, cliLibraryVersionsByCoordinate))
      .map(RuntimeExtensionLibraryDecision::missingLibraryFileNameOrThrowConflict)
      .flatMap(Optional::stream)
      .toList();
  }

  private static Set<String> cliLibraryFileNames(Set<RuntimeLibraryEntry> cliLibraries) {
    return cliLibraries.stream().map(RuntimeLibraryEntry::fileName).collect(Collectors.toSet());
  }

  private static Map<String, String> libraryVersionsByCoordinate(Set<RuntimeLibraryEntry> cliLibraries) {
    Map<String, Set<String>> cliLibraryVersionsByCoordinate = cliLibraries
      .stream()
      .map(RuntimeLibraryEntry::identity)
      .flatMap(Optional::stream)
      .collect(groupVersionsByCoordinate());
    failWhenCliContainsConflictingVersions(cliLibraryVersionsByCoordinate);
    return firstCliVersionByCoordinate(cliLibraryVersionsByCoordinate);
  }

  private static Collector<RuntimeLibraryIdentity, ?, Map<String, Set<String>>> groupVersionsByCoordinate() {
    return Collectors.groupingBy(
      RuntimeLibraryIdentity::coordinate,
      Collectors.mapping(RuntimeLibraryIdentity::version, Collectors.toSet())
    );
  }

  private static void failWhenCliContainsConflictingVersions(Map<String, Set<String>> cliLibraryVersionsByCoordinate) {
    cliLibraryVersionsByCoordinate
      .entrySet()
      .stream()
      .sorted(Map.Entry.comparingByKey())
      .filter(entry -> entry.getValue().size() > 1)
      .findFirst()
      .ifPresent(conflictEntry -> {
        String versions = conflictEntry.getValue().stream().sorted().collect(Collectors.joining(", "));
        throw new InvalidRuntimeConfigurationException(
          "CLI runtime library conflict detected for coordinate '"
            + conflictEntry.getKey()
            + "': multiple versions found ["
            + versions
            + "]."
        );
      });
  }

  private static Map<String, String> firstCliVersionByCoordinate(Map<String, Set<String>> cliLibraryVersionsByCoordinate) {
    return cliLibraryVersionsByCoordinate
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().iterator().next()));
  }

  private static RuntimeExtensionLibraryDecision decisionFor(
    RuntimeLibraryEntry extensionLibrary,
    Set<String> cliLibraryFileNames,
    Map<String, String> cliLibraryVersionsByCoordinate
  ) {
    Optional<String> conflictMessage = versionConflictMessage(extensionLibrary.identity(), cliLibraryVersionsByCoordinate).or(() ->
      missingIdentityShadowConflictMessage(extensionLibrary, cliLibraryFileNames)
    );
    if (conflictMessage.isPresent()) {
      return RuntimeExtensionLibraryDecision.conflict(conflictMessage.get());
    }

    if (extensionLibrary.identity().isEmpty()) {
      return RuntimeExtensionLibraryDecision.missing(extensionLibrary.fileName());
    }

    return extensionLibrary
        .identity()
        .filter(libraryIdentity -> cliLibraryVersionsByCoordinate.containsKey(libraryIdentity.coordinate()))
        .isPresent()
      ? RuntimeExtensionLibraryDecision.present()
      : RuntimeExtensionLibraryDecision.missing(extensionLibrary.fileName());
  }

  private static Optional<String> versionConflictMessage(
    Optional<RuntimeLibraryIdentity> extensionLibraryIdentity,
    Map<String, String> cliLibraryVersionsByCoordinate
  ) {
    return extensionLibraryIdentity.flatMap(libraryIdentity ->
      Optional.ofNullable(cliLibraryVersionsByCoordinate.get(libraryIdentity.coordinate())).flatMap(cliVersion ->
        conflictMessageWhenRequired(libraryIdentity, cliVersion)
      )
    );
  }

  private static Optional<String> conflictMessageWhenRequired(RuntimeLibraryIdentity libraryIdentity, String cliVersion) {
    RuntimeLibraryVersionComparison versionComparison = compareLibraryVersions(libraryIdentity.version(), cliVersion);

    return switch (versionComparison) {
      case EXTENSION_OLDER, SAME_VERSION -> Optional.empty();
      case EXTENSION_NEWER, UNCOMPARABLE -> Optional.of(
        "Extension runtime library conflict detected for coordinate '"
          + libraryIdentity.coordinate()
          + "': CLI uses version "
          + cliVersion
          + " while extension requires "
          + libraryIdentity.version()
          + "."
      );
    };
  }

  private static RuntimeLibraryVersionComparison compareLibraryVersions(String extensionVersion, String cliVersion) {
    Optional<List<Integer>> extensionVersionSegments = parsedVersionSegments(extensionVersion);
    Optional<List<Integer>> cliVersionSegments = parsedVersionSegments(cliVersion);
    if (extensionVersionSegments.isEmpty() || cliVersionSegments.isEmpty()) {
      return RuntimeLibraryVersionComparison.UNCOMPARABLE;
    }

    int comparison = compareVersionSegments(extensionVersionSegments.get(), cliVersionSegments.get());
    if (comparison < 0) {
      return RuntimeLibraryVersionComparison.EXTENSION_OLDER;
    }

    if (comparison > 0) {
      return RuntimeLibraryVersionComparison.EXTENSION_NEWER;
    }

    return RuntimeLibraryVersionComparison.SAME_VERSION;
  }

  private static int compareVersionSegments(List<Integer> leftSegments, List<Integer> rightSegments) {
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

  private static int versionSegment(List<Integer> segments, int index) {
    if (index >= segments.size()) {
      return 0;
    }

    return segments.get(index);
  }

  private static Optional<List<Integer>> parsedVersionSegments(String version) {
    String normalizedVersion = normalizedVersion(version);
    if (!numericVersion(normalizedVersion)) {
      return Optional.empty();
    }

    try {
      List<Integer> segments = List.of(normalizedVersion.split("\\.")).stream().map(Integer::parseInt).toList();
      return Optional.of(segments);
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  private static String normalizedVersion(String version) {
    if (version.startsWith("v") || version.startsWith("V")) {
      return version.substring(1);
    }

    return version;
  }

  private static boolean numericVersion(String version) {
    return version.matches("\\d+(\\.\\d+)*");
  }

  private enum RuntimeLibraryVersionComparison {
    EXTENSION_OLDER,
    EXTENSION_NEWER,
    SAME_VERSION,
    UNCOMPARABLE,
  }

  private static Optional<String> missingIdentityShadowConflictMessage(
    RuntimeLibraryEntry extensionLibrary,
    Set<String> cliLibraryFileNames
  ) {
    return extensionLibrary.identity().isEmpty()
      ? Optional.of(extensionLibrary.fileName())
          .filter(cliLibraryFileNames::contains)
          .map(
            libraryFileName ->
              "Extension runtime library '"
              + libraryFileName
              + "' has no inferable identity and collides with a CLI runtime library file name."
          )
      : Optional.empty();
  }

  private enum RuntimeExtensionLibraryDecisionKind {
    MISSING,
    PRESENT,
    CONFLICT,
  }

  private record RuntimeExtensionLibraryDecision(
    RuntimeExtensionLibraryDecisionKind kind,
    Optional<String> missingLibraryFileName,
    Optional<String> conflictMessage
  ) {
    private static RuntimeExtensionLibraryDecision missing(String missingLibraryFileName) {
      return new RuntimeExtensionLibraryDecision(
        RuntimeExtensionLibraryDecisionKind.MISSING,
        Optional.of(missingLibraryFileName),
        Optional.empty()
      );
    }

    private static RuntimeExtensionLibraryDecision present() {
      return new RuntimeExtensionLibraryDecision(RuntimeExtensionLibraryDecisionKind.PRESENT, Optional.empty(), Optional.empty());
    }

    private static RuntimeExtensionLibraryDecision conflict(String conflictMessage) {
      return new RuntimeExtensionLibraryDecision(
        RuntimeExtensionLibraryDecisionKind.CONFLICT,
        Optional.empty(),
        Optional.of(conflictMessage)
      );
    }

    private Optional<String> missingLibraryFileNameOrThrowConflict() {
      if (kind == RuntimeExtensionLibraryDecisionKind.CONFLICT) {
        throw new InvalidRuntimeConfigurationException(conflictMessage.orElseThrow());
      }
      return missingLibraryFileName;
    }
  }
}
