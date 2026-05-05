package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class RuntimeExtensionLoaderPathResolver {

  private static final String BOOT_INF_LIB_DIRECTORY = "BOOT-INF/lib/";
  private final RuntimeExtensionMissingLibrariesSelector missingLibrariesSelector = new RuntimeExtensionMissingLibrariesSelector();

  String resolve(Path overlayClassesPath, Path extensionJarPath, Path executableJarPath) {
    List<String> extensionLibraries = extensionLibraryFileNames(extensionJarPath);
    Set<String> cliLibraries = Set.copyOf(cliLibraryFileNames(executableJarPath));
    List<String> missingExtensionLibraries = missingLibrariesSelector.select(extensionLibraries, cliLibraries);
    if (missingExtensionLibraries.isEmpty()) {
      return overlayClassesPath.toString();
    }

    return Stream.concat(
      Stream.of(overlayClassesPath.toString()),
      missingExtensionLibraries
        .stream()
        .map(missingExtensionLibrary -> "jar:" + extensionJarPath.toUri() + "!/" + BOOT_INF_LIB_DIRECTORY + missingExtensionLibrary)
    ).collect(Collectors.joining(","));
  }

  private static List<String> extensionLibraryFileNames(Path extensionJarPath) {
    return libraryFileNames(extensionJarPath);
  }

  private static List<String> cliLibraryFileNames(Path executableJarPath) {
    try {
      return libraryFileNames(executableJarPath);
    } catch (InvalidRuntimeConfigurationException _) {
      return List.of();
    }
  }

  private static List<String> libraryFileNames(Path jarPath) {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      return jarFile
        .stream()
        .map(JarEntry::getName)
        .filter(RuntimeExtensionLoaderPathResolver::bootInfLibraryFile)
        .map(RuntimeExtensionLoaderPathResolver::libraryFileName)
        .toList();
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException(
        "Could not inspect runtime library entries from " + jarPath + ": " + ioException.getMessage()
      );
    }
  }

  private static boolean bootInfLibraryFile(String entryName) {
    return entryName.startsWith(BOOT_INF_LIB_DIRECTORY) && !entryName.endsWith("/") && entryName.endsWith(".jar");
  }

  private static String libraryFileName(String entryName) {
    return entryName.substring(BOOT_INF_LIB_DIRECTORY.length());
  }
}
