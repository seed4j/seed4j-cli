package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionMissingLibrariesSelector;
import com.seed4j.cli.bootstrap.domain.RuntimeLibraryEntry;
import com.seed4j.cli.bootstrap.domain.RuntimeLibraryIdentity;
import com.seed4j.cli.bootstrap.domain.RuntimeLibraryIdentityResolution;
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
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimeExtensionLoaderPathResolver implements com.seed4j.cli.bootstrap.domain.RuntimeExtensionLoaderPathResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeExtensionLoaderPathResolver.class);
  private static final String BOOT_INF_LIB_DIRECTORY = "BOOT-INF/lib/";
  private static final String MAVEN_METADATA_DIRECTORY = "META-INF/maven/";
  private static final String POM_PROPERTIES_SUFFIX = "/pom.properties";
  private static final String GROUP_ID_PROPERTY = "groupId";
  private static final String ARTIFACT_ID_PROPERTY = "artifactId";
  private static final String VERSION_PROPERTY = "version";
  private final RuntimeExtensionMissingLibrariesSelector missingLibrariesSelector = new RuntimeExtensionMissingLibrariesSelector();

  @Override
  public String resolve(Path overlayClassesPath, Path extensionJarPath, Path executableJarPath) {
    List<RuntimeLibraryEntry> extensionLibraries = extensionLibraries(extensionJarPath);
    Set<RuntimeLibraryEntry> cliLibraries = Set.copyOf(cliLibraries(executableJarPath));
    List<String> missingExtensionLibraries = missingLibrariesSelector.select(extensionLibraries, cliLibraries);
    if (missingExtensionLibraries.isEmpty()) {
      LOGGER.debug("No extension runtime libraries were added to loader.path from {}", extensionJarPath);
      return overlayClassesPath.toString();
    }

    LOGGER.debug("Extension runtime libraries added to loader.path from {}: {}", extensionJarPath, missingExtensionLibraries);

    return Stream.concat(
      Stream.of(overlayClassesPath.toString()),
      missingExtensionLibraries
        .stream()
        .map(missingExtensionLibrary -> "jar:" + extensionJarPath.toUri() + "!/" + BOOT_INF_LIB_DIRECTORY + missingExtensionLibrary)
    ).collect(Collectors.joining(","));
  }

  private static List<RuntimeLibraryEntry> extensionLibraries(Path extensionJarPath) {
    return strictLibraries(extensionJarPath);
  }

  private static List<RuntimeLibraryEntry> cliLibraries(Path executableJarPath) {
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
        .filter(RuntimeExtensionLoaderPathResolver::bootInfLibraryFile)
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
        .filter(RuntimeExtensionLoaderPathResolver::bootInfLibraryFile)
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
    Optional<RuntimeLibraryIdentity> metadataIdentity = strictRuntimeLibraryIdentityFromNestedJar(jarFile, jarEntry, libraryFileName);
    Optional<RuntimeLibraryIdentity> fileNameIdentity = RuntimeLibraryIdentity.fromJarFileName(libraryFileName);
    RuntimeLibraryIdentityResolution identityResolution = RuntimeLibraryIdentityResolution.from(metadataIdentity, fileNameIdentity);
    logOverrideIfNeeded(identityResolution, libraryFileName);
    return new RuntimeLibraryEntry(libraryFileName, identityResolution.effectiveIdentity());
  }

  private static void logOverrideIfNeeded(RuntimeLibraryIdentityResolution identityResolution, String libraryFileName) {
    identityResolution
      .metadataIdentity()
      .ifPresent(metadataLibraryIdentity ->
        identityResolution
          .fileNameIdentity()
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
      );
  }

  private static RuntimeLibraryEntry lenientRuntimeLibraryEntry(JarFile jarFile, JarEntry jarEntry) {
    String libraryFileName = libraryFileName(jarEntry.getName());
    Optional<RuntimeLibraryIdentity> libraryIdentity = lenientRuntimeLibraryIdentityFromNestedJar(jarFile, jarEntry).or(() ->
      RuntimeLibraryIdentity.fromJarFileName(libraryFileName)
    );
    return new RuntimeLibraryEntry(libraryFileName, libraryIdentity);
  }

  private static String libraryFileName(String entryName) {
    return entryName.substring(BOOT_INF_LIB_DIRECTORY.length());
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Nested jar I/O failure paths are environment-dependent")
  private static Optional<RuntimeLibraryIdentity> strictRuntimeLibraryIdentityFromNestedJar(
    JarFile jarFile,
    JarEntry jarEntry,
    String libraryFileName
  ) {
    try (InputStream jarInputStream = jarFile.getInputStream(jarEntry)) {
      return strictRuntimeLibraryIdentityFromNestedJar(jarInputStream, libraryFileName);
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError(
        "Could not inspect nested library metadata from " + jarEntry.getName() + ".",
        ioException
      );
    }
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Nested jar I/O failure paths are environment-dependent")
  private static Optional<RuntimeLibraryIdentity> lenientRuntimeLibraryIdentityFromNestedJar(JarFile jarFile, JarEntry jarEntry) {
    try (InputStream jarInputStream = jarFile.getInputStream(jarEntry)) {
      return lenientRuntimeLibraryIdentityFromNestedJar(jarInputStream);
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError(
        "Could not inspect nested library metadata from " + jarEntry.getName() + ".",
        ioException
      );
    }
  }

  private static Optional<RuntimeLibraryIdentity> strictRuntimeLibraryIdentityFromNestedJar(
    InputStream nestedJarInputStream,
    String libraryFileName
  ) {
    return strictRuntimeLibraryIdentityFromNestedJarWithFallback(nestedJarInputStream, libraryFileName);
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Nested jar I/O failure paths are environment-dependent")
  private static Optional<RuntimeLibraryIdentity> strictRuntimeLibraryIdentityFromNestedJarWithFallback(
    InputStream nestedJarInputStream,
    String libraryFileName
  ) {
    try {
      return strictRuntimeLibraryIdentityFromNestedJarInternal(nestedJarInputStream, libraryFileName);
    } catch (IOException _) {
      return Optional.empty();
    }
  }

  private static Optional<RuntimeLibraryIdentity> strictRuntimeLibraryIdentityFromNestedJarInternal(
    InputStream nestedJarInputStream,
    String libraryFileName
  ) throws IOException {
    Set<RuntimeLibraryIdentity> resolvedLibraryIdentities = new LinkedHashSet<>();
    try (JarInputStream jarInputStream = new JarInputStream(nestedJarInputStream)) {
      for (
        JarEntry nestedJarEntry = jarInputStream.getNextJarEntry();
        nestedJarEntry != null;
        nestedJarEntry = jarInputStream.getNextJarEntry()
      ) {
        if (!pomPropertiesEntry(nestedJarEntry)) {
          continue;
        }

        RuntimeLibraryIdentity runtimeLibraryIdentity = runtimeLibraryIdentityFromPomProperties(jarInputStream).orElseThrow(() ->
          incompleteRuntimeLibraryMetadata(libraryFileName)
        );
        resolvedLibraryIdentities.add(runtimeLibraryIdentity);
      }
      return strictSingleIdentityOrThrowConflict(resolvedLibraryIdentities, libraryFileName);
    }
  }

  private static Optional<RuntimeLibraryIdentity> strictSingleIdentityOrThrowConflict(
    Set<RuntimeLibraryIdentity> resolvedLibraryIdentities,
    String libraryFileName
  ) {
    if (resolvedLibraryIdentities.size() > 1) {
      throw conflictingRuntimeLibraryMetadata(libraryFileName, resolvedLibraryIdentities);
    }

    return resolvedLibraryIdentities.stream().findFirst();
  }

  private static Optional<RuntimeLibraryIdentity> lenientRuntimeLibraryIdentityFromNestedJar(InputStream nestedJarInputStream) {
    try (JarInputStream jarInputStream = new JarInputStream(nestedJarInputStream)) {
      for (
        JarEntry nestedJarEntry = jarInputStream.getNextJarEntry();
        nestedJarEntry != null;
        nestedJarEntry = jarInputStream.getNextJarEntry()
      ) {
        if (pomPropertiesEntry(nestedJarEntry)) {
          return runtimeLibraryIdentityFromPomProperties(jarInputStream);
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

  private static InvalidRuntimeConfigurationException conflictingRuntimeLibraryMetadata(
    String libraryFileName,
    Set<RuntimeLibraryIdentity> runtimeLibraryIdentities
  ) {
    String identities = runtimeLibraryIdentities
      .stream()
      .map(runtimeLibraryIdentity -> runtimeLibraryIdentity.coordinate() + ":" + runtimeLibraryIdentity.version())
      .sorted()
      .collect(Collectors.joining(", "));
    return new InvalidRuntimeConfigurationException(
      "Runtime library metadata for '" + libraryFileName + "' is conflicting: multiple identities found [" + identities + "]."
    );
  }

  private static String mavenCoordinate(String groupId, String artifactId) {
    return groupId + ":" + artifactId;
  }
}
