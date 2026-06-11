package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionPackageValidator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarRuntimeExtensionPackageValidator implements RuntimeExtensionPackageValidator {

  private static final String BOOT_INF_CLASSES_DIRECTORY = "BOOT-INF/classes/";
  private static final String BOOT_INF_CLASSES_DIRECTORY_WITHOUT_TRAILING_SLASH = "BOOT-INF/classes";

  @Override
  public void validate(RuntimeExtensionJarPath extensionJarPath) {
    if (hasBootInfClasses(extensionJarPath.path())) {
      return;
    }

    throw invalidRuntimeJarLayout(extensionJarPath.path());
  }

  private boolean hasBootInfClasses(Path extensionJarPath) {
    try (JarFile extensionJarFile = new JarFile(extensionJarPath.toFile())) {
      return extensionJarFile.stream().map(JarEntry::getName).anyMatch(JarRuntimeExtensionPackageValidator::bootInfClassesEntry);
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError(invalidRuntimeJarLayoutMessage(extensionJarPath), ioException);
    }
  }

  private static boolean bootInfClassesEntry(String entryName) {
    return (
      BOOT_INF_CLASSES_DIRECTORY_WITHOUT_TRAILING_SLASH.equals(entryName)
      || BOOT_INF_CLASSES_DIRECTORY.equals(entryName)
      || entryName.startsWith(BOOT_INF_CLASSES_DIRECTORY)
    );
  }

  private InvalidRuntimeConfigurationException invalidRuntimeJarLayout(Path extensionJarPath) {
    return new InvalidRuntimeConfigurationException(invalidRuntimeJarLayoutMessage(extensionJarPath));
  }

  private String invalidRuntimeJarLayoutMessage(Path extensionJarPath) {
    return "Invalid runtime jar file: " + extensionJarPath + ". Expected a Spring Boot fat jar containing BOOT-INF/classes.";
  }
}
