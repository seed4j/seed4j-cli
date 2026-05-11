package com.seed4j.cli.bootstrap.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

final class RuntimeExtensionMissingLibrariesSelector {

  List<String> select(List<RuntimeLibraryEntry> extensionLibraries, Set<RuntimeLibraryEntry> cliLibraries) {
    Map<String, String> cliLibraryVersionsByCoordinate = libraryVersionsByCoordinate(cliLibraries);
    Set<String> cliLibraryFileNames = cliLibraries.stream().map(RuntimeLibraryEntry::fileName).collect(Collectors.toSet());

    return extensionLibraries
      .stream()
      .map(extensionLibrary -> ensureNoVersionConflict(extensionLibrary, cliLibraryVersionsByCoordinate))
      .filter(missingFrom(cliLibraryFileNames))
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

  private static String ensureNoVersionConflict(RuntimeLibraryEntry extensionLibrary, Map<String, String> cliLibraryVersionsByCoordinate) {
    failWhenVersionConflict(extensionLibrary.identity(), cliLibraryVersionsByCoordinate);
    return extensionLibrary.fileName();
  }

  private static void failWhenVersionConflict(
    Optional<RuntimeLibraryIdentity> extensionLibraryIdentity,
    Map<String, String> cliLibraryVersionsByCoordinate
  ) {
    extensionLibraryIdentity.ifPresent(libraryIdentity ->
      Optional.ofNullable(cliLibraryVersionsByCoordinate.get(libraryIdentity.coordinate()))
        .filter(cliVersion -> !cliVersion.equals(libraryIdentity.version()))
        .ifPresent(cliVersion -> {
          throw new InvalidRuntimeConfigurationException(
            "Extension runtime library conflict detected for coordinate '"
              + libraryIdentity.coordinate()
              + "': CLI uses version "
              + cliVersion
              + " while extension requires "
              + libraryIdentity.version()
              + "."
          );
        })
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

  private static Collector<RuntimeLibraryIdentity, ?, Map<String, Set<String>>> groupVersionsByCoordinate() {
    return Collectors.groupingBy(
      RuntimeLibraryIdentity::coordinate,
      Collectors.mapping(RuntimeLibraryIdentity::version, Collectors.toSet())
    );
  }

  private static Predicate<String> missingFrom(Set<String> cliLibraries) {
    return extensionLibraryFileName -> !cliLibraries.contains(extensionLibraryFileName);
  }
}
