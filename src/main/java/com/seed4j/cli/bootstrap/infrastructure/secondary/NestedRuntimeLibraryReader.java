package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeLibraryEntry;
import com.seed4j.cli.bootstrap.domain.RuntimeLibraryIdentity;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NestedRuntimeLibraryReader {

  static final String BOOT_INF_LIB_DIRECTORY = "BOOT-INF/lib/";

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeExtensionLoaderPathResolver.class);
  private static final String MAVEN_METADATA_DIRECTORY = "META-INF/maven/";
  private static final String POM_PROPERTIES_SUFFIX = "/pom.properties";
  private static final String GROUP_ID_PROPERTY = "groupId";
  private static final String ARTIFACT_ID_PROPERTY = "artifactId";
  private static final String VERSION_PROPERTY = "version";

  List<RuntimeLibraryEntry> extensionLibraries(Path extensionJarPath) {
    return strictLibraries(extensionJarPath);
  }

  List<RuntimeLibraryEntry> cliLibraries(Path executableJarPath) {
    try {
      return lenientLibraries(executableJarPath);
    } catch (InvalidRuntimeConfigurationException _) {
      return List.of();
    }
  }

  private static List<RuntimeLibraryEntry> strictLibraries(Path jarPath) {
    return strictLibrariesWithFailureMapping(jarPath);
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Jar open/read failure paths are environment-dependent")
  private static List<RuntimeLibraryEntry> strictLibrariesWithFailureMapping(Path jarPath) {
    try {
      return strictLibrariesInternal(jarPath);
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError(
        "Could not inspect runtime library entries from " + jarPath + ".",
        ioException
      );
    }
  }

  private static List<RuntimeLibraryEntry> strictLibrariesInternal(Path jarPath) throws IOException {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      return jarFile
        .stream()
        .filter(NestedRuntimeLibraryReader::bootInfLibraryFile)
        .map(jarEntry -> strictRuntimeLibraryEntry(jarFile, jarEntry))
        .toList();
    }
  }

  private static List<RuntimeLibraryEntry> lenientLibraries(Path jarPath) {
    return lenientLibrariesWithFailureMapping(jarPath);
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Jar open/read failure paths are environment-dependent")
  private static List<RuntimeLibraryEntry> lenientLibrariesWithFailureMapping(Path jarPath) {
    try {
      return lenientLibrariesInternal(jarPath);
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError(
        "Could not inspect runtime library entries from " + jarPath + ".",
        ioException
      );
    }
  }

  private static List<RuntimeLibraryEntry> lenientLibrariesInternal(Path jarPath) throws IOException {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      return jarFile
        .stream()
        .filter(NestedRuntimeLibraryReader::bootInfLibraryFile)
        .map(jarEntry -> lenientRuntimeLibraryEntry(jarFile, jarEntry))
        .toList();
    }
  }

  private static boolean bootInfLibraryFile(JarEntry jarEntry) {
    return (
      jarEntry.getName().startsWith(BOOT_INF_LIB_DIRECTORY)
      && !jarEntry.getName().endsWith("/")
      && jarEntry.getName().toLowerCase(Locale.ROOT).endsWith(".jar")
    );
  }

  private static RuntimeLibraryEntry strictRuntimeLibraryEntry(JarFile jarFile, JarEntry jarEntry) {
    String libraryFileName = libraryFileName(jarEntry.getName());
    Optional<MavenRuntimeLibraryMetadata> metadata = strictRuntimeLibraryMetadataFromNestedJar(jarFile, jarEntry, libraryFileName);
    Optional<RuntimeLibraryIdentity> metadataIdentity = metadata.map(MavenRuntimeLibraryMetadata::logicalIdentity);
    Optional<RuntimeLibraryIdentity> fileNameIdentity = RuntimeLibraryIdentity.fromJarFileName(libraryFileName);
    logOverrideIfNeeded(metadata, metadataIdentity, fileNameIdentity, libraryFileName);
    return new RuntimeLibraryEntry(libraryFileName, metadataIdentity.or(() -> fileNameIdentity));
  }

  private static void logOverrideIfNeeded(
    Optional<MavenRuntimeLibraryMetadata> metadata,
    Optional<RuntimeLibraryIdentity> metadataIdentity,
    Optional<RuntimeLibraryIdentity> fileNameIdentity,
    String libraryFileName
  ) {
    metadata
      .filter(mavenMetadata -> !mavenMetadata.expectedFileName().equals(libraryFileName))
      .ifPresent(mavenMetadata ->
        metadataIdentity.ifPresent(metadataLibraryIdentity ->
          fileNameIdentity
            .filter(inferredLibraryIdentity -> !metadataLibraryIdentity.equals(inferredLibraryIdentity))
            .ifPresent(inferredLibraryIdentity ->
              LOGGER.debug(
                "Using pom.properties identity {}:{} for '{}' instead of file name inferred identity {}:{}",
                metadataLibraryIdentity.coordinate(),
                metadataLibraryIdentity.version(),
                libraryFileName,
                inferredLibraryIdentity.coordinate(),
                inferredLibraryIdentity.version()
              )
            )
        )
      );
  }

  private static RuntimeLibraryEntry lenientRuntimeLibraryEntry(JarFile jarFile, JarEntry jarEntry) {
    String libraryFileName = libraryFileName(jarEntry.getName());
    Optional<RuntimeLibraryIdentity> libraryIdentity = lenientRuntimeLibraryMetadataFromNestedJar(jarFile, jarEntry)
      .map(MavenRuntimeLibraryMetadata::logicalIdentity)
      .or(() -> RuntimeLibraryIdentity.fromJarFileName(libraryFileName));
    return new RuntimeLibraryEntry(libraryFileName, libraryIdentity);
  }

  private static String libraryFileName(String entryName) {
    return entryName.substring(BOOT_INF_LIB_DIRECTORY.length());
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Nested jar I/O failure paths are environment-dependent")
  private static Optional<MavenRuntimeLibraryMetadata> strictRuntimeLibraryMetadataFromNestedJar(
    JarFile jarFile,
    JarEntry jarEntry,
    String libraryFileName
  ) {
    try (InputStream jarInputStream = jarFile.getInputStream(jarEntry)) {
      return strictRuntimeLibraryMetadataFromNestedJar(jarInputStream, libraryFileName);
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError(
        "Could not inspect nested library metadata from " + jarEntry.getName() + ".",
        ioException
      );
    }
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Nested jar I/O failure paths are environment-dependent")
  private static Optional<MavenRuntimeLibraryMetadata> lenientRuntimeLibraryMetadataFromNestedJar(JarFile jarFile, JarEntry jarEntry) {
    try (InputStream jarInputStream = jarFile.getInputStream(jarEntry)) {
      return lenientRuntimeLibraryMetadataFromNestedJar(jarInputStream);
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError(
        "Could not inspect nested library metadata from " + jarEntry.getName() + ".",
        ioException
      );
    }
  }

  private static Optional<MavenRuntimeLibraryMetadata> strictRuntimeLibraryMetadataFromNestedJar(
    InputStream nestedJarInputStream,
    String libraryFileName
  ) {
    return strictRuntimeLibraryMetadataFromNestedJarWithFallback(nestedJarInputStream, libraryFileName);
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Nested jar I/O failure paths are environment-dependent")
  private static Optional<MavenRuntimeLibraryMetadata> strictRuntimeLibraryMetadataFromNestedJarWithFallback(
    InputStream nestedJarInputStream,
    String libraryFileName
  ) {
    try {
      return strictRuntimeLibraryMetadataFromNestedJarInternal(nestedJarInputStream, libraryFileName);
    } catch (IOException _) {
      return Optional.empty();
    }
  }

  private static Optional<MavenRuntimeLibraryMetadata> strictRuntimeLibraryMetadataFromNestedJarInternal(
    InputStream nestedJarInputStream,
    String libraryFileName
  ) throws IOException {
    Set<MavenRuntimeLibraryMetadata> resolvedLibraryMetadata = new LinkedHashSet<>();
    try (JarInputStream jarInputStream = new JarInputStream(nestedJarInputStream)) {
      for (
        JarEntry nestedJarEntry = jarInputStream.getNextJarEntry();
        nestedJarEntry != null;
        nestedJarEntry = jarInputStream.getNextJarEntry()
      ) {
        if (!pomPropertiesEntry(nestedJarEntry)) {
          continue;
        }

        MavenRuntimeLibraryMetadata runtimeLibraryMetadata = runtimeLibraryMetadataFromPomProperties(jarInputStream).orElseThrow(() ->
          incompleteRuntimeLibraryMetadata(libraryFileName)
        );
        resolvedLibraryMetadata.add(runtimeLibraryMetadata);
      }
      return strictSingleMetadataOrThrowConflict(resolvedLibraryMetadata, libraryFileName);
    }
  }

  private static Optional<MavenRuntimeLibraryMetadata> strictSingleMetadataOrThrowConflict(
    Set<MavenRuntimeLibraryMetadata> resolvedLibraryMetadata,
    String libraryFileName
  ) {
    if (resolvedLibraryMetadata.size() > 1) {
      throw conflictingRuntimeLibraryMetadata(libraryFileName, resolvedLibraryMetadata);
    }

    return resolvedLibraryMetadata.stream().findFirst();
  }

  private static Optional<MavenRuntimeLibraryMetadata> lenientRuntimeLibraryMetadataFromNestedJar(InputStream nestedJarInputStream) {
    try (JarInputStream jarInputStream = new JarInputStream(nestedJarInputStream)) {
      for (
        JarEntry nestedJarEntry = jarInputStream.getNextJarEntry();
        nestedJarEntry != null;
        nestedJarEntry = jarInputStream.getNextJarEntry()
      ) {
        if (pomPropertiesEntry(nestedJarEntry)) {
          return runtimeLibraryMetadataFromPomProperties(jarInputStream);
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

  private static Optional<MavenRuntimeLibraryMetadata> runtimeLibraryMetadataFromPomProperties(InputStream pomPropertiesInputStream)
    throws IOException {
    Properties properties = new Properties();
    properties.load(pomPropertiesInputStream);
    return runtimeLibraryMetadataFromPomProperties(properties);
  }

  private static Optional<MavenRuntimeLibraryMetadata> runtimeLibraryMetadataFromPomProperties(Properties properties) {
    Optional<String> groupId = propertyValue(properties, GROUP_ID_PROPERTY);
    Optional<String> artifactId = propertyValue(properties, ARTIFACT_ID_PROPERTY);
    Optional<String> version = propertyValue(properties, VERSION_PROPERTY);
    return groupId.flatMap(mavenGroup ->
      artifactId.flatMap(artifact -> version.map(libraryVersion -> new MavenRuntimeLibraryMetadata(mavenGroup, artifact, libraryVersion)))
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

  private static InvalidRuntimeConfigurationException conflictingRuntimeLibraryMetadata(
    String libraryFileName,
    Set<MavenRuntimeLibraryMetadata> runtimeLibraryMetadata
  ) {
    String identities = runtimeLibraryMetadata
      .stream()
      .map(MavenRuntimeLibraryMetadata::logicalIdentity)
      .map(runtimeLibraryIdentity -> runtimeLibraryIdentity.coordinate() + ":" + runtimeLibraryIdentity.version())
      .sorted()
      .collect(Collectors.joining(", "));
    return new InvalidRuntimeConfigurationException(
      "Runtime library metadata for '" + libraryFileName + "' is conflicting: multiple identities found [" + identities + "]."
    );
  }

  private record MavenRuntimeLibraryMetadata(String groupId, String artifactId, String version) {
    private RuntimeLibraryIdentity logicalIdentity() {
      return new RuntimeLibraryIdentity(coordinate(), version);
    }

    private String coordinate() {
      return groupId + ":" + artifactId;
    }

    private String expectedFileName() {
      return artifactId + "-" + version + ".jar";
    }
  }
}
