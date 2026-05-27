package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

final class RuntimeExtensionStartClassResolver {

  String resolve(Path extensionJarPath) {
    try (JarFile extensionJarFile = new JarFile(extensionJarPath.toFile())) {
      Manifest manifest = extensionJarFile.getManifest();
      if (manifest == null) {
        throw invalidStartClass(extensionJarPath, "Missing manifest Start-Class.");
      }

      String startClass = manifest.getMainAttributes().getValue("Start-Class");
      if (startClass == null || startClass.isBlank()) {
        throw invalidStartClass(extensionJarPath, "Missing manifest Start-Class.");
      }

      String resolvedStartClass = startClass.trim();
      String classEntryName = "BOOT-INF/classes/" + resolvedStartClass.replace('.', '/') + ".class";
      if (extensionJarFile.getEntry(classEntryName) == null) {
        throw invalidStartClass(extensionJarPath, "Start-Class does not exist inside BOOT-INF/classes: " + resolvedStartClass);
      }

      return resolvedStartClass;
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError(
        invalidStartClassMessage(extensionJarPath, "Could not read manifest Start-Class."),
        ioException
      );
    }
  }

  private InvalidRuntimeConfigurationException invalidStartClass(Path extensionJarPath, String reason) {
    return new InvalidRuntimeConfigurationException(invalidStartClassMessage(extensionJarPath, reason));
  }

  private String invalidStartClassMessage(Path extensionJarPath, String reason) {
    return "Invalid runtime jar file: " + extensionJarPath + ". " + reason;
  }
}
