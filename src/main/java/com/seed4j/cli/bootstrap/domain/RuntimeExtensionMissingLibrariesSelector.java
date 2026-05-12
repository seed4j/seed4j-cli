package com.seed4j.cli.bootstrap.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

final class RuntimeExtensionMissingLibrariesSelector {

  List<String> select(List<RuntimeLibraryEntry> extensionLibraries, Set<RuntimeLibraryEntry> cliLibraries) {
    CliRuntimeLibraries cliRuntimeLibraries = CliRuntimeLibraries.from(cliLibraries);
    Map<String, String> cliLibraryVersionsByCoordinate = libraryVersionsByCoordinate(cliLibraries);

    return extensionLibraries
      .stream()
      .map(extensionLibrary -> decisionFor(extensionLibrary, cliRuntimeLibraries, cliLibraryVersionsByCoordinate))
      .map(RuntimeExtensionLibraryDecision::missingLibraryFileNameOrThrowConflict)
      .flatMap(Optional::stream)
      .toList();
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
    CliRuntimeLibraries cliRuntimeLibraries,
    Map<String, String> cliLibraryVersionsByCoordinate
  ) {
    Optional<String> conflictMessage = versionConflictMessage(extensionLibrary.identity(), cliLibraryVersionsByCoordinate).or(() ->
      missingIdentityShadowConflictMessage(extensionLibrary, cliRuntimeLibraries.fileNames())
    );
    if (conflictMessage.isPresent()) {
      return RuntimeExtensionLibraryDecision.conflict(conflictMessage.get());
    }

    if (extensionLibrary.identity().isEmpty()) {
      return RuntimeExtensionLibraryDecision.missing(extensionLibrary.fileName());
    }

    return extensionLibrary.identity().filter(cliRuntimeLibraries::containsIdentity).isPresent()
      ? RuntimeExtensionLibraryDecision.present()
      : RuntimeExtensionLibraryDecision.missing(extensionLibrary.fileName());
  }

  private static Optional<String> versionConflictMessage(
    Optional<RuntimeLibraryIdentity> extensionLibraryIdentity,
    Map<String, String> cliLibraryVersionsByCoordinate
  ) {
    return extensionLibraryIdentity.flatMap(libraryIdentity ->
      Optional.ofNullable(cliLibraryVersionsByCoordinate.get(libraryIdentity.coordinate()))
        .filter(cliVersion -> !cliVersion.equals(libraryIdentity.version()))
        .map(
          cliVersion ->
            "Extension runtime library conflict detected for coordinate '"
            + libraryIdentity.coordinate()
            + "': CLI uses version "
            + cliVersion
            + " while extension requires "
            + libraryIdentity.version()
            + "."
        )
    );
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

  private record CliRuntimeLibraries(Set<String> fileNames, Set<RuntimeLibraryIdentity> identities) {
    private static CliRuntimeLibraries from(Set<RuntimeLibraryEntry> cliLibraries) {
      Set<String> fileNames = cliLibraries.stream().map(RuntimeLibraryEntry::fileName).collect(Collectors.toSet());
      Set<RuntimeLibraryIdentity> identities = cliLibraries
        .stream()
        .map(RuntimeLibraryEntry::identity)
        .flatMap(Optional::stream)
        .collect(Collectors.toSet());
      return new CliRuntimeLibraries(fileNames, identities);
    }

    private boolean containsIdentity(RuntimeLibraryIdentity identity) {
      return identities.contains(identity);
    }
  }
}
