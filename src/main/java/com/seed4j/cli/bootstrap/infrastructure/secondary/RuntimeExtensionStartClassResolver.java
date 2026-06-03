package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class RuntimeExtensionStartClassResolver implements com.seed4j.cli.bootstrap.domain.RuntimeExtensionStartClassResolver {

  @Override
  public String resolve(Path extensionJarPath) {
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

  private static InvalidRuntimeConfigurationException invalidStartClass(Path extensionJarPath, String reason) {
    return new InvalidRuntimeConfigurationException(invalidStartClassMessage(extensionJarPath, reason));
  }

  private static String invalidStartClassMessage(Path extensionJarPath, String reason) {
    return "Invalid runtime jar file: " + extensionJarPath + ". " + reason;
  }
}
