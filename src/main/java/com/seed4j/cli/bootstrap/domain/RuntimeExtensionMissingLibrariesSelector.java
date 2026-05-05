package com.seed4j.cli.bootstrap.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class RuntimeExtensionMissingLibrariesSelector {

  List<String> select(List<String> extensionLibraries, Set<String> cliLibraries) {
    Map<String, String> cliLibraryVersionsByCoordinate = libraryVersionsByCoordinate(cliLibraries);

    return extensionLibraries
      .stream()
      .map(extensionLibrary -> ensureNoVersionConflict(extensionLibrary, cliLibraryVersionsByCoordinate))
      .filter(missingFrom(cliLibraries))
      .toList();
  }

  private static Map<String, String> libraryVersionsByCoordinate(Set<String> cliLibraries) {
    return cliLibraries
      .stream()
      .map(RuntimeLibraryIdentity::fromJarFileName)
      .flatMap(Optional::stream)
      .collect(Collectors.toMap(RuntimeLibraryIdentity::coordinate, RuntimeLibraryIdentity::version, keepFirstVersion()));
  }

  private static BinaryOperator<String> keepFirstVersion() {
    return (first, _) -> first;
  }

  private static String ensureNoVersionConflict(String extensionLibrary, Map<String, String> cliLibraryVersionsByCoordinate) {
    failWhenVersionConflict(extensionLibrary, cliLibraryVersionsByCoordinate);
    return extensionLibrary;
  }

  private static void failWhenVersionConflict(String extensionLibrary, Map<String, String> cliLibraryVersionsByCoordinate) {
    RuntimeLibraryIdentity.fromJarFileName(extensionLibrary).ifPresent(libraryIdentity ->
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

  private static Predicate<String> missingFrom(Set<String> cliLibraries) {
    return extensionLibrary -> !cliLibraries.contains(extensionLibrary);
  }
}
