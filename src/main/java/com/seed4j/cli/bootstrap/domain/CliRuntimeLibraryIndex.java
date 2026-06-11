package com.seed4j.cli.bootstrap.domain;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

record CliRuntimeLibraryIndex(
  Set<RuntimeLibraryFileName> fileNames,
  Map<RuntimeLibraryCoordinate, Set<RuntimeLibraryVersion>> versionsByCoordinate
) {
  static CliRuntimeLibraryIndex from(Set<RuntimeLibraryEntry> cliLibraries) {
    Set<RuntimeLibraryFileName> fileNames = cliLibraries.stream().map(RuntimeLibraryEntry::fileName).collect(Collectors.toSet());
    Map<RuntimeLibraryCoordinate, Set<RuntimeLibraryVersion>> versionsByCoordinate = cliLibraries
      .stream()
      .map(RuntimeLibraryEntry::identity)
      .flatMap(Optional::stream)
      .collect(groupVersionsByCoordinate());
    failWhenContainsConflictingVersions(versionsByCoordinate);
    return new CliRuntimeLibraryIndex(fileNames, versionsByCoordinate);
  }

  private static Collector<
    RuntimeLibraryIdentity,
    ?,
    Map<RuntimeLibraryCoordinate, Set<RuntimeLibraryVersion>>
  > groupVersionsByCoordinate() {
    return Collectors.groupingBy(
      RuntimeLibraryIdentity::coordinate,
      Collectors.mapping(RuntimeLibraryIdentity::version, Collectors.toSet())
    );
  }

  private static void failWhenContainsConflictingVersions(Map<RuntimeLibraryCoordinate, Set<RuntimeLibraryVersion>> versionsByCoordinate) {
    versionsByCoordinate
      .entrySet()
      .stream()
      .sorted(Map.Entry.comparingByKey((left, right) -> left.value().compareTo(right.value())))
      .filter(entry -> entry.getValue().size() > 1)
      .findFirst()
      .ifPresent(conflictEntry -> {
        String versions = conflictEntry.getValue().stream().map(RuntimeLibraryVersion::value).sorted().collect(Collectors.joining(", "));
        throw new InvalidRuntimeConfigurationException(
          "CLI runtime library conflict detected for coordinate '"
            + conflictEntry.getKey().value()
            + "': multiple versions found ["
            + versions
            + "]."
        );
      });
  }

  boolean containsFileName(RuntimeLibraryFileName libraryFileName) {
    return fileNames.contains(libraryFileName);
  }

  Optional<RuntimeLibraryVersion> versionForCoordinate(RuntimeLibraryCoordinate coordinate) {
    return Optional.ofNullable(versionsByCoordinate.get(coordinate)).flatMap(versions -> versions.stream().findFirst());
  }
}
