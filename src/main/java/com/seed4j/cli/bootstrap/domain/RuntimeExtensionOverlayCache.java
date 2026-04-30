package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

final class RuntimeExtensionOverlayCache {

  private static final String BOOT_INF_CLASSES_DIRECTORY = "BOOT-INF/classes/";
  private static final String BOOT_INF_CLASSES_DIRECTORY_WITHOUT_TRAILING_SLASH = "BOOT-INF/classes";
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

  @ExcludeFromGeneratedCodeCoverage(reason = "Cache publication race branch depends on filesystem concurrency timing")
  private static void moveStagingDirectory(Path stagingDirectoryPath, Path cacheDirectoryPath) throws IOException {
    try {
      Files.move(stagingDirectoryPath, cacheDirectoryPath, StandardCopyOption.ATOMIC_MOVE);
    } catch (FileAlreadyExistsException _) {
      deleteDirectoryQuietly(stagingDirectoryPath);
    }
  }

  private static void deleteDirectoryQuietly(Path directoryPath) {
    try {
      if (!Files.exists(directoryPath)) {
        return;
      }

      try (Stream<Path> walk = Files.walk(directoryPath)) {
        walk
          .sorted(Comparator.reverseOrder())
          .forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException _) {
              // Best effort cleanup.
            }
          });
      }
    } catch (IOException _) {
      // Best effort cleanup.
    }
  }

  private void extractBootInfClasses(Path extensionJarPath, Path classesDirectoryPath) throws IOException {
    boolean bootInfClassesFound = false;
    try (JarFile extensionJarFile = new JarFile(extensionJarPath.toFile())) {
      for (JarEntry jarEntry : extensionJarFile.stream().toList()) {
        String jarEntryName = jarEntry.getName();
        if (BOOT_INF_CLASSES_DIRECTORY_WITHOUT_TRAILING_SLASH.equals(jarEntryName) || BOOT_INF_CLASSES_DIRECTORY.equals(jarEntryName)) {
          bootInfClassesFound = true;
          continue;
        }

        if (!jarEntryName.startsWith(BOOT_INF_CLASSES_DIRECTORY)) {
          continue;
        }

        bootInfClassesFound = true;
        String relativeEntryPath = jarEntryName.substring(BOOT_INF_CLASSES_DIRECTORY.length());
        if (relativeEntryPath.isBlank()) {
          continue;
        }

        Path overlayEntryPath = classesDirectoryPath.resolve(relativeEntryPath).normalize();
        if (!overlayEntryPath.startsWith(classesDirectoryPath)) {
          throw new InvalidRuntimeConfigurationException("Invalid runtime extension entry path: " + jarEntryName);
        }

        if (jarEntry.isDirectory()) {
          Files.createDirectories(overlayEntryPath);
          continue;
        }

        Files.createDirectories(overlayEntryPath.getParent());
        try (InputStream jarEntryInputStream = extensionJarFile.getInputStream(jarEntry)) {
          Files.copy(jarEntryInputStream, overlayEntryPath, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }

    if (!bootInfClassesFound) {
      throw new InvalidRuntimeConfigurationException(
        "Invalid runtime jar file: " + extensionJarPath + ". Expected a Spring Boot fat jar containing BOOT-INF/classes."
      );
    }
  }
}
