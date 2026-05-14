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
    return new CliRuntimeLibraryIndex(fileNames, versionsByCoordinate);
  }

  private static Collector<RuntimeLibraryIdentity, ?, Map<String, Set<String>>> groupVersionsByCoordinate() {
    return Collectors.groupingBy(
      RuntimeLibraryIdentity::coordinate,
      Collectors.mapping(RuntimeLibraryIdentity::version, Collectors.toSet())
    );
  }
}
