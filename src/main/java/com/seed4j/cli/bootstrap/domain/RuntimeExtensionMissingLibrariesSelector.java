package com.seed4j.cli.bootstrap.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RuntimeExtensionMissingLibrariesSelector {

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeExtensionMissingLibrariesSelector.class);

  List<String> select(List<RuntimeLibraryEntry> extensionLibraries, Set<RuntimeLibraryEntry> cliLibraries) {
    CliRuntimeLibraryIndex cliRuntimeLibraryIndex = CliRuntimeLibraryIndex.from(cliLibraries);

    return extensionLibraries
      .stream()
      .map(extensionLibrary -> decisionFor(extensionLibrary, cliRuntimeLibraryIndex))
      .map(RuntimeExtensionLibraryDecision::missingLibraryFileNameOrThrowConflict)
      .flatMap(Optional::stream)
      .toList();
  }

  private static RuntimeExtensionLibraryDecision decisionFor(
    RuntimeLibraryEntry extensionLibrary,
    CliRuntimeLibraryIndex cliRuntimeLibraryIndex
  ) {
    Optional<String> conflictMessage = versionConflictMessage(extensionLibrary.identity(), cliRuntimeLibraryIndex).or(() ->
      missingIdentityShadowConflictMessage(extensionLibrary, cliRuntimeLibraryIndex)
    );
    if (conflictMessage.isPresent()) {
      return RuntimeExtensionLibraryDecision.conflict(conflictMessage.get());
    }

    if (extensionLibrary.identity().isEmpty()) {
      return RuntimeExtensionLibraryDecision.missing(extensionLibrary.fileName());
    }

    if (matchesCliLibraryCoordinate(extensionLibrary.identity(), cliRuntimeLibraryIndex)) {
      return RuntimeExtensionLibraryDecision.present();
    }

    return RuntimeExtensionLibraryDecision.missing(extensionLibrary.fileName());
  }

  private static boolean matchesCliLibraryCoordinate(
    Optional<RuntimeLibraryIdentity> extensionLibraryIdentity,
    CliRuntimeLibraryIndex cliRuntimeLibraryIndex
  ) {
    return extensionLibraryIdentity
      .map(RuntimeLibraryIdentity::coordinate)
      .flatMap(cliRuntimeLibraryIndex::versionForCoordinate)
      .isPresent();
  }

  private static Optional<String> versionConflictMessage(
    Optional<RuntimeLibraryIdentity> extensionLibraryIdentity,
    CliRuntimeLibraryIndex cliRuntimeLibraryIndex
  ) {
    return extensionLibraryIdentity.flatMap(libraryIdentity ->
      cliRuntimeLibraryIndex
        .versionForCoordinate(libraryIdentity.coordinate())
        .flatMap(cliVersion -> conflictMessageWhenRequired(libraryIdentity, cliVersion))
    );
  }

  private static Optional<String> conflictMessageWhenRequired(RuntimeLibraryIdentity libraryIdentity, String cliVersion) {
    RuntimeLibraryVersionComparison versionComparison = compareLibraryVersions(libraryIdentity.version(), cliVersion);

    return switch (versionComparison) {
      case EXTENSION_OLDER -> {
        LOGGER.debug(
          "Keeping CLI runtime library for coordinate '{}' because CLI version {} is newer than extension version {}",
          libraryIdentity.coordinate(),
          cliVersion,
          libraryIdentity.version()
        );
        yield Optional.empty();
      }
      case SAME_VERSION -> Optional.empty();
      case EXTENSION_NEWER -> Optional.of(
        "Extension runtime library conflict detected for coordinate '"
          + libraryIdentity.coordinate()
          + "': CLI uses version "
          + cliVersion
          + " while extension requires "
          + libraryIdentity.version()
          + "."
      );
      case UNCOMPARABLE -> Optional.of(
        "Extension runtime library conflict detected for coordinate '"
          + libraryIdentity.coordinate()
          + "': CLI version "
          + cliVersion
          + " and extension version "
          + libraryIdentity.version()
          + " are not safely comparable."
      );
    };
  }

  private static RuntimeLibraryVersionComparison compareLibraryVersions(String extensionVersion, String cliVersion) {
    if (normalizedVersion(extensionVersion).equals(normalizedVersion(cliVersion))) {
      return RuntimeLibraryVersionComparison.SAME_VERSION;
    }

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
    CliRuntimeLibraryIndex cliRuntimeLibraryIndex
  ) {
    return extensionLibrary.identity().isEmpty()
      ? Optional.of(extensionLibrary.fileName())
          .filter(cliRuntimeLibraryIndex::containsFileName)
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
