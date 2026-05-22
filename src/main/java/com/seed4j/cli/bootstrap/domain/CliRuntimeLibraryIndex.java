package com.seed4j.cli.bootstrap.domain;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

record CliRuntimeLibraryIndex(Set<String> fileNames, Map<String, Set<String>> versionsByCoordinate) {
  static CliRuntimeLibraryIndex from(Set<RuntimeLibraryEntry> cliLibraries) {
    Set<String> fileNames = cliLibraries.stream().map(RuntimeLibraryEntry::fileName).collect(Collectors.toSet());
    Map<String, Set<String>> versionsByCoordinate = cliLibraries
      .stream()
      .map(RuntimeLibraryEntry::identity)
      .flatMap(Optional::stream)
      .collect(groupVersionsByCoordinate());
    failWhenContainsConflictingVersions(versionsByCoordinate);
    return new CliRuntimeLibraryIndex(fileNames, versionsByCoordinate);
  }

  private static Collector<RuntimeLibraryIdentity, ?, Map<String, Set<String>>> groupVersionsByCoordinate() {
    return Collectors.groupingBy(
      RuntimeLibraryIdentity::coordinate,
      Collectors.mapping(RuntimeLibraryIdentity::version, Collectors.toSet())
    );
  }

  private static void failWhenContainsConflictingVersions(Map<String, Set<String>> versionsByCoordinate) {
    versionsByCoordinate
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

  boolean containsFileName(String libraryFileName) {
    return fileNames.contains(libraryFileName);
  }

  Optional<String> versionForCoordinate(String coordinate) {
    return Optional.ofNullable(versionsByCoordinate.get(coordinate)).flatMap(versions -> versions.stream().findFirst());
  }
}
