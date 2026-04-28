package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class RuntimeExtensionJarLayoutValidator {

  private static final String BOOT_INF_CLASSES_DIRECTORY = "BOOT-INF/classes/";
  private static final String BOOT_INF_CLASSES_DIRECTORY_WITHOUT_TRAILING_SLASH = "BOOT-INF/classes";

  void validate(Path extensionJarPath) {
    if (hasBootInfClasses(extensionJarPath)) {
      return;
    }

    throw invalidRuntimeJarLayout(extensionJarPath);
  }

  private boolean hasBootInfClasses(Path extensionJarPath) {
    try (JarFile extensionJarFile = new JarFile(extensionJarPath.toFile())) {
      return extensionJarFile.stream().map(JarEntry::getName).anyMatch(RuntimeExtensionJarLayoutValidator::isBootInfClassesEntry);
    } catch (IOException _) {
      throw invalidRuntimeJarLayout(extensionJarPath);
    }
  }

  private static boolean isBootInfClassesEntry(String entryName) {
    return (
      BOOT_INF_CLASSES_DIRECTORY_WITHOUT_TRAILING_SLASH.equals(entryName)
      || BOOT_INF_CLASSES_DIRECTORY.equals(entryName)
      || entryName.startsWith(BOOT_INF_CLASSES_DIRECTORY)
    );
  }

  private InvalidRuntimeConfigurationException invalidRuntimeJarLayout(Path extensionJarPath) {
    return new InvalidRuntimeConfigurationException(
      "Invalid runtime jar file: " + extensionJarPath + ". Expected a Spring Boot fat jar containing BOOT-INF/classes."
    );
  }
}
