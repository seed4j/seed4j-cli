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
      .map(RuntimeLibraryFileName::value)
      .toList();
  }

  private static RuntimeExtensionLibraryDecision decisionFor(
    RuntimeLibraryEntry extensionLibrary,
    CliRuntimeLibraryIndex cliRuntimeLibraryIndex
  ) {
    Optional<RuntimeExtensionLibraryConflictMessage> conflictMessage = versionConflictMessage(
      extensionLibrary.identity(),
      cliRuntimeLibraryIndex
    ).or(() -> missingIdentityShadowConflictMessage(extensionLibrary, cliRuntimeLibraryIndex));
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

  private static Optional<RuntimeExtensionLibraryConflictMessage> versionConflictMessage(
    Optional<RuntimeLibraryIdentity> extensionLibraryIdentity,
    CliRuntimeLibraryIndex cliRuntimeLibraryIndex
  ) {
    return extensionLibraryIdentity.flatMap(libraryIdentity ->
      cliRuntimeLibraryIndex
        .versionForCoordinate(libraryIdentity.coordinate())
        .flatMap(cliVersion -> conflictMessageWhenRequired(libraryIdentity, cliVersion))
    );
  }

  private static Optional<RuntimeExtensionLibraryConflictMessage> conflictMessageWhenRequired(
    RuntimeLibraryIdentity libraryIdentity,
    RuntimeLibraryVersion cliVersion
  ) {
    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison versionComparison = RUNTIME_LIBRARY_VERSION_COMPARATOR.compare(
      libraryIdentity.version().value(),
      cliVersion.value()
    );

    return switch (versionComparison) {
      case EXTENSION_OLDER -> Optional.empty();
      case SAME_VERSION -> Optional.empty();
      case EXTENSION_NEWER -> Optional.of(
        new RuntimeExtensionLibraryConflictMessage(
          "Extension runtime library conflict detected for coordinate '"
            + libraryIdentity.coordinate().value()
            + "': CLI uses version "
            + cliVersion.value()
            + " while extension requires "
            + libraryIdentity.version().value()
            + "."
        )
      );
      case UNCOMPARABLE -> Optional.of(
        new RuntimeExtensionLibraryConflictMessage(
          "Extension runtime library conflict detected for coordinate '"
            + libraryIdentity.coordinate().value()
            + "': CLI version "
            + cliVersion.value()
            + " and extension version "
            + libraryIdentity.version().value()
            + " are not safely comparable."
        )
      );
    };
  }

  private static Optional<RuntimeExtensionLibraryConflictMessage> missingIdentityShadowConflictMessage(
    RuntimeLibraryEntry extensionLibrary,
    CliRuntimeLibraryIndex cliRuntimeLibraryIndex
  ) {
    return extensionLibrary.identity().isEmpty()
      ? Optional.of(extensionLibrary.fileName())
          .filter(cliRuntimeLibraryIndex::containsFileName)
          .map(libraryFileName ->
            new RuntimeExtensionLibraryConflictMessage(
              "Extension runtime library '"
                + libraryFileName.value()
                + "' has no inferable identity and collides with a CLI runtime library file name."
            )
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
    Optional<RuntimeLibraryFileName> missingLibraryFileName,
    Optional<RuntimeExtensionLibraryConflictMessage> conflictMessage
  ) {
    private static RuntimeExtensionLibraryDecision missing(RuntimeLibraryFileName missingLibraryFileName) {
      return new RuntimeExtensionLibraryDecision(
        RuntimeExtensionLibraryDecisionKind.MISSING,
        Optional.of(missingLibraryFileName),
        Optional.empty()
      );
    }

    private static RuntimeExtensionLibraryDecision present() {
      return new RuntimeExtensionLibraryDecision(RuntimeExtensionLibraryDecisionKind.PRESENT, Optional.empty(), Optional.empty());
    }

    private static RuntimeExtensionLibraryDecision conflict(RuntimeExtensionLibraryConflictMessage conflictMessage) {
      return new RuntimeExtensionLibraryDecision(
        RuntimeExtensionLibraryDecisionKind.CONFLICT,
        Optional.empty(),
        Optional.of(conflictMessage)
      );
    }

    private Optional<RuntimeLibraryFileName> missingLibraryFileNameOrThrowConflict() {
      if (kind == RuntimeExtensionLibraryDecisionKind.CONFLICT) {
        throw new InvalidRuntimeConfigurationException(conflictMessage.orElseThrow().value());
      }
      return missingLibraryFileName;
    }
  }

  private record RuntimeExtensionLibraryConflictMessage(String value) {}
}
