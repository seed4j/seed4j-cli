package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

final class RuntimeExtensionOverlayCache {

  private static final String BOOT_INF_CLASSES_DIRECTORY = "BOOT-INF/classes/";
  private static final String BOOT_INF_CLASSES_DIRECTORY_WITHOUT_TRAILING_SLASH = "BOOT-INF/classes";
  private static final String APPLICATION_RESOURCE_PREFIX = "config/application";
  private static final List<String> APPLICATION_RESOURCE_SUFFIXES = List.of(".yml", ".yaml", ".properties");
  private static final Path RUNTIME_CACHE_DIRECTORY = Path.of(".config", "seed4j-cli", "runtime", "cache");

  private final Path userHome;
  private final RuntimeExtensionCacheIdentityResolver cacheIdentityResolver = new RuntimeExtensionCacheIdentityResolver();

  RuntimeExtensionOverlayCache(Path userHome) {
    this.userHome = userHome;
  }

  Path materialize(Path extensionJarPath) {
    RuntimeExtensionCacheIdentity cacheIdentity = cacheIdentityResolver.resolve(extensionJarPath);
    Path cacheRootDirectoryPath = userHome.resolve(RUNTIME_CACHE_DIRECTORY);
    Path cacheDirectoryPath = cacheRootDirectoryPath.resolve(cacheIdentity.value());
    Path classesDirectoryPath = cacheDirectoryPath.resolve("classes");
    Path stagingDirectoryPath = cacheRootDirectoryPath.resolve("." + cacheIdentity.value() + ".staging-" + UUID.randomUUID());
    if (Files.isDirectory(classesDirectoryPath)) {
      return classesDirectoryPath;
    }

    try {
      Files.createDirectories(cacheRootDirectoryPath);
      Path stagingClassesDirectoryPath = stagingDirectoryPath.resolve("classes");
      Files.createDirectories(stagingClassesDirectoryPath);
      extractBootInfClasses(extensionJarPath, stagingClassesDirectoryPath);
      moveStagingDirectory(stagingDirectoryPath, cacheDirectoryPath);
      return classesDirectoryPath;
    } catch (InvalidRuntimeConfigurationException invalidRuntimeConfigurationException) {
      deleteDirectoryQuietly(stagingDirectoryPath);
      throw invalidRuntimeConfigurationException;
    } catch (IOException ioException) {
      deleteDirectoryQuietly(stagingDirectoryPath);
      throw new InvalidRuntimeConfigurationException(
        "Could not materialize runtime extension overlay cache for " + extensionJarPath + ": " + ioException.getMessage()
      );
    }
  }

  private void extractBootInfClasses(Path extensionJarPath, Path classesDirectoryPath) throws IOException {
    try (JarFile extensionJarFile = new JarFile(extensionJarPath.toFile())) {
      requireBootInfClassesPresence(extensionJarPath, extensionJarFile);
      materializeBootInfClassesEntries(extensionJarFile, classesDirectoryPath);
    } catch (UncheckedIOException uncheckedIOException) {
      throw uncheckedIOException.getCause();
    }
  }

  private static void requireBootInfClassesPresence(Path extensionJarPath, JarFile extensionJarFile) {
    if (missingBootInfClasses(extensionJarFile)) {
      throw new InvalidRuntimeConfigurationException(
        "Invalid runtime jar file: " + extensionJarPath + ". Expected a Spring Boot fat jar containing BOOT-INF/classes."
      );
    }
  }

  private static boolean missingBootInfClasses(JarFile extensionJarFile) {
    return extensionJarFile.stream().noneMatch(RuntimeExtensionOverlayCache::relevantBootInfClassesEntry);
  }

  private static boolean relevantBootInfClassesEntry(JarEntry jarEntry) {
    return bootInfClassesMarker(jarEntry) || bootInfClassesEntry(jarEntry);
  }

  private static boolean bootInfClassesMarker(JarEntry jarEntry) {
    String jarEntryName = jarEntry.getName();
    return BOOT_INF_CLASSES_DIRECTORY_WITHOUT_TRAILING_SLASH.equals(jarEntryName) || BOOT_INF_CLASSES_DIRECTORY.equals(jarEntryName);
  }

  private static boolean bootInfClassesEntry(JarEntry jarEntry) {
    return jarEntry.getName().startsWith(BOOT_INF_CLASSES_DIRECTORY);
  }

  private void materializeBootInfClassesEntries(JarFile extensionJarFile, Path classesDirectoryPath) {
    bootInfClassesEntries(extensionJarFile)
      .map(jarEntry -> resolveExtractionTarget(jarEntry, classesDirectoryPath))
      .flatMap(Optional::stream)
      .forEachOrdered(entryExtractionTarget -> copyEntryToOverlay(extensionJarFile, entryExtractionTarget));
  }

  private static Stream<JarEntry> bootInfClassesEntries(JarFile extensionJarFile) {
    return extensionJarFile.stream().filter(RuntimeExtensionOverlayCache::relevantBootInfClassesEntry);
  }

  private static Optional<EntryExtractionTarget> resolveExtractionTarget(JarEntry jarEntry, Path classesDirectoryPath) {
    if (bootInfClassesMarker(jarEntry)) {
      return Optional.empty();
    }

    String jarEntryName = jarEntry.getName();
    String relativeEntryPath = jarEntryName.substring(BOOT_INF_CLASSES_DIRECTORY.length());
    if (relativeEntryPath.isBlank()) {
      return Optional.empty();
    }
    if (globalRuntimeResource(relativeEntryPath)) {
      return Optional.empty();
    }

    Path overlayEntryPath = classesDirectoryPath.resolve(relativeEntryPath).normalize();
    if (!overlayEntryPath.startsWith(classesDirectoryPath)) {
      throw new InvalidRuntimeConfigurationException("Invalid runtime extension entry path: " + jarEntryName);
    }

    return Optional.of(new EntryExtractionTarget(jarEntry, overlayEntryPath));
  }

  private static boolean globalRuntimeResource(String relativeEntryPath) {
    return applicationConfigurationResource(relativeEntryPath) || logbackConfigurationResource(relativeEntryPath);
  }

  private static boolean applicationConfigurationResource(String relativeEntryPath) {
    if (!relativeEntryPath.startsWith(APPLICATION_RESOURCE_PREFIX)) {
      return false;
    }

    return APPLICATION_RESOURCE_SUFFIXES.stream().anyMatch(relativeEntryPath::endsWith);
  }

  private static boolean logbackConfigurationResource(String relativeEntryPath) {
    if (relativeEntryPath.contains("/")) {
      return false;
    }

    return relativeEntryPath.startsWith("logback") && relativeEntryPath.endsWith(".xml");
  }

  private static void copyEntryToOverlay(JarFile extensionJarFile, EntryExtractionTarget entryExtractionTarget) {
    JarEntry jarEntry = entryExtractionTarget.jarEntry();
    Path overlayEntryPath = entryExtractionTarget.overlayEntryPath();
    try {
      if (jarEntry.isDirectory()) {
        Files.createDirectories(overlayEntryPath);
      } else {
        Files.createDirectories(overlayEntryPath.getParent());
        try (InputStream jarEntryInputStream = extensionJarFile.getInputStream(jarEntry)) {
          Files.copy(jarEntryInputStream, overlayEntryPath, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    } catch (IOException ioException) {
      throw new UncheckedIOException(ioException);
    }
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Cache publication race branch depends on filesystem concurrency timing")
  private static void moveStagingDirectory(Path stagingDirectoryPath, Path cacheDirectoryPath) throws IOException {
    try {
      Files.move(stagingDirectoryPath, cacheDirectoryPath, StandardCopyOption.ATOMIC_MOVE);
    } catch (FileAlreadyExistsException _) {
      deleteDirectoryQuietly(stagingDirectoryPath);
    }
  }

  private static void deleteDirectoryQuietly(Path directoryPath) {
    if (Files.notExists(directoryPath)) {
      return;
    }

    deleteDirectoryTreeQuietly(directoryPath);
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Best-effort directory walk failures are nondeterministic across file systems")
  private static void deleteDirectoryTreeQuietly(Path directoryPath) {
    try {
      try (Stream<Path> walk = Files.walk(directoryPath)) {
        walk.sorted(Comparator.reverseOrder()).forEach(RuntimeExtensionOverlayCache::deletePathQuietly);
      }
    } catch (IOException _) {
      return;
    }
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Best-effort file deletion failures are nondeterministic across operating systems")
  private static void deletePathQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException _) {
      return;
    }
  }

  private record EntryExtractionTarget(JarEntry jarEntry, Path overlayEntryPath) {}
}
