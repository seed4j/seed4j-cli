package com.seed4j.cli.bootstrap.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class RuntimeExtensionMissingLibrariesSelector {

  private static final RuntimeLibraryVersionComparator RUNTIME_LIBRARY_VERSION_COMPARATOR = new RuntimeLibraryVersionComparator();

  public List<String> select(List<RuntimeLibraryEntry> extensionLibraries, Set<RuntimeLibraryEntry> cliLibraries) {
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
    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison versionComparison = RUNTIME_LIBRARY_VERSION_COMPARATOR.compare(
      libraryIdentity.version(),
      cliVersion
    );

    return switch (versionComparison) {
      case EXTENSION_OLDER -> {
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
