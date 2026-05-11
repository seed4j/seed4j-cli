package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class RuntimeExtensionLoaderPathResolver {

  private static final String BOOT_INF_LIB_DIRECTORY = "BOOT-INF/lib/";
  private static final String POM_PROPERTIES_SUFFIX = "/pom.properties";
  private final RuntimeExtensionMissingLibrariesSelector missingLibrariesSelector = new RuntimeExtensionMissingLibrariesSelector();

  String resolve(Path overlayClassesPath, Path extensionJarPath, Path executableJarPath) {
    List<RuntimeLibraryEntry> extensionLibraries = extensionLibraries(extensionJarPath);
    Set<RuntimeLibraryEntry> cliLibraries = Set.copyOf(cliLibraries(executableJarPath));
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

  private static List<RuntimeLibraryEntry> extensionLibraries(Path extensionJarPath) {
    return libraries(extensionJarPath);
  }

  private static List<RuntimeLibraryEntry> cliLibraries(Path executableJarPath) {
    try {
      return libraries(executableJarPath);
    } catch (InvalidRuntimeConfigurationException _) {
      return List.of();
    }
  }

  private static List<RuntimeLibraryEntry> libraries(Path jarPath) {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      return jarFile
        .stream()
        .filter(RuntimeExtensionLoaderPathResolver::bootInfLibraryFile)
        .map(jarEntry -> runtimeLibraryEntry(jarFile, jarEntry))
        .toList();
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException(
        "Could not inspect runtime library entries from " + jarPath + ": " + ioException.getMessage()
      );
    }
  }

  private static RuntimeLibraryEntry runtimeLibraryEntry(JarFile jarFile, JarEntry jarEntry) {
    String libraryFileName = libraryFileName(jarEntry.getName());
    Optional<RuntimeLibraryIdentity> libraryIdentity = runtimeLibraryIdentityFromNestedJar(jarFile, jarEntry).or(() ->
      RuntimeLibraryIdentity.fromJarFileName(libraryFileName)
    );
    return new RuntimeLibraryEntry(libraryFileName, libraryIdentity);
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Nested jar I/O failure paths are environment-dependent")
  private static Optional<RuntimeLibraryIdentity> runtimeLibraryIdentityFromNestedJar(JarFile jarFile, JarEntry jarEntry) {
    try (InputStream jarInputStream = jarFile.getInputStream(jarEntry)) {
      return runtimeLibraryIdentityFromNestedJar(jarInputStream);
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException(
        "Could not inspect nested library metadata from " + jarEntry.getName() + ": " + ioException.getMessage()
      );
    }
  }

  private static Optional<RuntimeLibraryIdentity> runtimeLibraryIdentityFromNestedJar(InputStream nestedJarInputStream) {
    try (JarInputStream jarInputStream = new JarInputStream(nestedJarInputStream)) {
      JarEntry nestedJarEntry = jarInputStream.getNextJarEntry();
      while (nestedJarEntry != null) {
        if (nestedJarEntry.getName().startsWith("META-INF/maven/") && nestedJarEntry.getName().endsWith(POM_PROPERTIES_SUFFIX)) {
          return runtimeLibraryIdentityFromPomProperties(jarInputStream);
        }
        nestedJarEntry = jarInputStream.getNextJarEntry();
      }
      return Optional.empty();
    } catch (IOException _) {
      return Optional.empty();
    }
  }

  private static Optional<RuntimeLibraryIdentity> runtimeLibraryIdentityFromPomProperties(InputStream pomPropertiesInputStream)
    throws IOException {
    Properties properties = new Properties();
    properties.load(pomPropertiesInputStream);

    String groupId = properties.getProperty("groupId");
    String artifactId = properties.getProperty("artifactId");
    String version = properties.getProperty("version");
    if (groupId == null || artifactId == null || version == null) {
      return Optional.empty();
    }

    return Optional.of(new RuntimeLibraryIdentity(groupId + ":" + artifactId, version));
  }

  private static boolean bootInfLibraryFile(JarEntry jarEntry) {
    return bootInfLibraryFile(jarEntry.getName());
  }

  private static boolean bootInfLibraryFile(String entryName) {
    return entryName.startsWith(BOOT_INF_LIB_DIRECTORY) && !entryName.endsWith("/") && entryName.endsWith(".jar");
  }

  private static String libraryFileName(String entryName) {
    return entryName.substring(BOOT_INF_LIB_DIRECTORY.length());
  }
}
