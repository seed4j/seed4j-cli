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
    CliRuntimeLibraries cliRuntimeLibraries = CliRuntimeLibraries.from(cliLibraries);
    Map<String, String> cliLibraryVersionsByCoordinate = libraryVersionsByCoordinate(cliLibraries);
    failWhenMissingIdentityShadowsCliLibrary(extensionLibraries, cliRuntimeLibraries.fileNames());

    return extensionLibraries
      .stream()
      .map(extensionLibrary -> ensureNoVersionConflict(extensionLibrary, cliLibraryVersionsByCoordinate))
      .filter(missingFrom(cliRuntimeLibraries))
      .map(RuntimeLibraryEntry::fileName)
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

  private static RuntimeLibraryEntry ensureNoVersionConflict(
    RuntimeLibraryEntry extensionLibrary,
    Map<String, String> cliLibraryVersionsByCoordinate
  ) {
    failWhenVersionConflict(extensionLibrary.identity(), cliLibraryVersionsByCoordinate);
    return extensionLibrary;
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

  private static Predicate<RuntimeLibraryEntry> missingFrom(CliRuntimeLibraries cliRuntimeLibraries) {
    return extensionLibrary ->
      missingByFileName(extensionLibrary, cliRuntimeLibraries) && missingByIdentity(extensionLibrary, cliRuntimeLibraries);
  }

  private static boolean missingByFileName(RuntimeLibraryEntry extensionLibrary, CliRuntimeLibraries cliRuntimeLibraries) {
    return !cliRuntimeLibraries.containsFileName(extensionLibrary.fileName());
  }

  private static boolean missingByIdentity(RuntimeLibraryEntry extensionLibrary, CliRuntimeLibraries cliRuntimeLibraries) {
    return extensionLibrary
      .identity()
      .map(identity -> !cliRuntimeLibraries.containsIdentity(identity))
      .orElse(true);
  }

  private static void failWhenMissingIdentityShadowsCliLibrary(
    List<RuntimeLibraryEntry> extensionLibraries,
    Set<String> cliLibraryFileNames
  ) {
    extensionLibraries
      .stream()
      .filter(extensionLibrary -> extensionLibrary.identity().isEmpty())
      .map(RuntimeLibraryEntry::fileName)
      .filter(cliLibraryFileNames::contains)
      .findFirst()
      .ifPresent(libraryFileName -> {
        throw new InvalidRuntimeConfigurationException(
          "Extension runtime library '" + libraryFileName + "' has no inferable identity and collides with a CLI runtime library file name."
        );
      });
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

    private boolean containsFileName(String fileName) {
      return fileNames.contains(fileName);
    }

    private boolean containsIdentity(RuntimeLibraryIdentity identity) {
      return identities.contains(identity);
    }
  }
}
