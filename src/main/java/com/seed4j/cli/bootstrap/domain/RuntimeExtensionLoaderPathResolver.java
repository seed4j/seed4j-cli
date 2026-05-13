package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
  private static final String MAVEN_METADATA_DIRECTORY = "META-INF/maven/";
  private static final String POM_PROPERTIES_SUFFIX = "/pom.properties";
  private static final String GROUP_ID_PROPERTY = "groupId";
  private static final String ARTIFACT_ID_PROPERTY = "artifactId";
  private static final String VERSION_PROPERTY = "version";
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
    return libraries(extensionJarPath, true);
  }

  private static List<RuntimeLibraryEntry> cliLibraries(Path executableJarPath) {
    try {
      return libraries(executableJarPath, false);
    } catch (InvalidRuntimeConfigurationException _) {
      return List.of();
    }
  }

  private static List<RuntimeLibraryEntry> libraries(Path jarPath, boolean strictPomMetadata) {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      return jarFile
        .stream()
        .filter(RuntimeExtensionLoaderPathResolver::bootInfLibraryFile)
        .map(jarEntry -> runtimeLibraryEntry(jarFile, jarEntry, strictPomMetadata))
        .toList();
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException(
        "Could not inspect runtime library entries from " + jarPath + ": " + ioException.getMessage()
      );
    }
  }

  private static boolean bootInfLibraryFile(JarEntry jarEntry) {
    return (
      jarEntry.getName().startsWith(BOOT_INF_LIB_DIRECTORY)
      && !jarEntry.getName().endsWith("/")
      && jarEntry.getName().toLowerCase(Locale.ROOT).endsWith(".jar")
    );
  }

  private static RuntimeLibraryEntry runtimeLibraryEntry(JarFile jarFile, JarEntry jarEntry, boolean strictPomMetadata) {
    String libraryFileName = libraryFileName(jarEntry.getName());
    Optional<RuntimeLibraryIdentity> libraryIdentity = runtimeLibraryIdentityFromNestedJar(
      jarFile,
      jarEntry,
      libraryFileName,
      strictPomMetadata
    ).or(() -> RuntimeLibraryIdentity.fromJarFileName(libraryFileName));
    return new RuntimeLibraryEntry(libraryFileName, libraryIdentity);
  }

  private static String libraryFileName(String entryName) {
    return entryName.substring(BOOT_INF_LIB_DIRECTORY.length());
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Nested jar I/O failure paths are environment-dependent")
  private static Optional<RuntimeLibraryIdentity> runtimeLibraryIdentityFromNestedJar(
    JarFile jarFile,
    JarEntry jarEntry,
    String libraryFileName,
    boolean strictPomMetadata
  ) {
    try (InputStream jarInputStream = jarFile.getInputStream(jarEntry)) {
      return runtimeLibraryIdentityFromNestedJar(jarInputStream, libraryFileName, strictPomMetadata);
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException(
        "Could not inspect nested library metadata from " + jarEntry.getName() + ": " + ioException.getMessage()
      );
    }
  }

  private static Optional<RuntimeLibraryIdentity> runtimeLibraryIdentityFromNestedJar(
    InputStream nestedJarInputStream,
    String libraryFileName,
    boolean strictPomMetadata
  ) {
    try (JarInputStream jarInputStream = new JarInputStream(nestedJarInputStream)) {
      for (
        JarEntry nestedJarEntry = jarInputStream.getNextJarEntry();
        nestedJarEntry != null;
        nestedJarEntry = jarInputStream.getNextJarEntry()
      ) {
        if (pomPropertiesEntry(nestedJarEntry)) {
          Optional<RuntimeLibraryIdentity> runtimeLibraryIdentity = runtimeLibraryIdentityFromPomProperties(jarInputStream);
          if (runtimeLibraryIdentity.isPresent()) {
            return runtimeLibraryIdentity;
          }
          if (strictPomMetadata) {
            throw incompleteRuntimeLibraryMetadata(libraryFileName);
          }
          return Optional.empty();
        }
      }
      return Optional.empty();
    } catch (IOException _) {
      return Optional.empty();
    }
  }

  private static boolean pomPropertiesEntry(JarEntry jarEntry) {
    return jarEntry.getName().startsWith(MAVEN_METADATA_DIRECTORY) && jarEntry.getName().endsWith(POM_PROPERTIES_SUFFIX);
  }

  private static Optional<RuntimeLibraryIdentity> runtimeLibraryIdentityFromPomProperties(InputStream pomPropertiesInputStream)
    throws IOException {
    Properties properties = new Properties();
    properties.load(pomPropertiesInputStream);
    return runtimeLibraryIdentityFromPomProperties(properties);
  }

  private static Optional<RuntimeLibraryIdentity> runtimeLibraryIdentityFromPomProperties(Properties properties) {
    Optional<String> groupId = propertyValue(properties, GROUP_ID_PROPERTY);
    Optional<String> artifactId = propertyValue(properties, ARTIFACT_ID_PROPERTY);
    Optional<String> version = propertyValue(properties, VERSION_PROPERTY);
    return groupId.flatMap(mavenGroup ->
      artifactId.flatMap(artifact ->
        version.map(libraryVersion -> new RuntimeLibraryIdentity(mavenCoordinate(mavenGroup, artifact), libraryVersion))
      )
    );
  }

  private static Optional<String> propertyValue(Properties properties, String propertyName) {
    return Optional.ofNullable(properties.getProperty(propertyName));
  }

  private static InvalidRuntimeConfigurationException incompleteRuntimeLibraryMetadata(String libraryFileName) {
    return new InvalidRuntimeConfigurationException(
      "Runtime library metadata for '" + libraryFileName + "' is incomplete: pom.properties must define groupId, artifactId and version."
    );
  }

  private static String mavenCoordinate(String groupId, String artifactId) {
    return groupId + ":" + artifactId;
  }
}
