package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@UnitTest
class RuntimeExtensionLoaderPathResolverTest {

  @Test
  void shouldNotLogDebugWhenPomPropertiesIdentityMatchesExpectedMavenFileName() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-cli-", ".jar"),
      List.of(new LibraryWithPomCoordinates("shared-lib-1.0.0.jar", "com.acme", "shared-lib", "1.0.0"))
    );
    Path extensionJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of(new LibraryWithPomCoordinates("shared-lib-1.0.0.jar", "com.acme", "shared-lib", "1.0.0"))
    );
    Logger logger = (Logger) LoggerFactory.getLogger(RuntimeExtensionLoaderPathResolver.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);

    try {
      new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list)
      .extracting(ILoggingEvent::getFormattedMessage)
      .noneMatch(message -> message.contains("Using pom.properties identity"));
  }

  @Test
  void shouldNotLogDebugWhenRenamedFileNameInfersPomPropertiesIdentity() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-cli-", ".jar"),
      List.of(new LibraryWithPomCoordinates("shared-lib-1.0.0.jar", "com.acme", "shared-lib", "1.0.0"))
    );
    Path extensionJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of(new LibraryWithPomCoordinates("com.acme:shared-lib-1.0.0.jar", "com.acme", "shared-lib", "1.0.0"))
    );
    Logger logger = (Logger) LoggerFactory.getLogger(RuntimeExtensionLoaderPathResolver.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);

    try {
      new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list)
      .extracting(ILoggingEvent::getFormattedMessage)
      .noneMatch(message -> message.contains("Using pom.properties identity"));
  }

  @Test
  void shouldLogDebugWhenPomPropertiesIdentityOverridesFileNameInExtensionLibrary() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-cli-", ".jar"),
      List.of(new LibraryWithPomCoordinates("shared-lib-1.0.0.jar", "com.acme", "shared-lib", "1.0.0"))
    );
    Path extensionJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of(new LibraryWithPomCoordinates("shared-lib-2.0.0.jar", "com.acme", "shared-lib", "1.0.0"))
    );
    Logger logger = (Logger) LoggerFactory.getLogger(RuntimeExtensionLoaderPathResolver.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);

    try {
      new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list)
      .extracting(ILoggingEvent::getFormattedMessage)
      .anyMatch(message -> message.contains("shared-lib-2.0.0.jar") && message.contains("com.acme:shared-lib:1.0.0"));
  }

  @Test
  void shouldReportAllDistinctConflictingPomIdentitiesFromExtensionNestedJar() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-cli-", ".jar"),
      List.of(new LibraryWithPomCoordinates("shared-lib-1.0.0.jar", "com.acme", "shared-lib", "1.0.0"))
    );
    Path extensionJarPath = createJarWithBootInfLibraryContainingMultiplePomCoordinates(
      Files.createTempFile("seed4j-extension-", ".jar"),
      "extension-shaded.jar",
      List.of(
        new LibraryWithPomCoordinates("ignored-1.jar", "com.acme", "shared-lib", "1.0.0"),
        new LibraryWithPomCoordinates("ignored-2.jar", "org.other", "other-lib", "2.0.0"),
        new LibraryWithPomCoordinates("ignored-3.jar", "io.third", "third-lib", "3.0.0")
      )
    );

    assertThatThrownBy(() -> new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("extension-shaded.jar")
      .hasMessageContaining("com.acme:shared-lib:1.0.0")
      .hasMessageContaining("org.other:other-lib:2.0.0")
      .hasMessageContaining("io.third:third-lib:3.0.0");
  }

  @Test
  void shouldReportTechnicalDetailsWhenExtensionJarCannotBeInspected() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibraries(Files.createTempFile("seed4j-cli-", ".jar"), List.of("shared-lib-1.0.0.jar"));
    Path extensionJarPath = Files.createTempDirectory("seed4j-extension-not-a-jar-");

    assertThatThrownBy(() -> new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not inspect runtime library entries from " + extensionJarPath + ".")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldFailFastWhenExtensionNestedJarContainsConflictingPomIdentities() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-cli-", ".jar"),
      List.of(new LibraryWithPomCoordinates("shared-lib-1.0.0.jar", "com.acme", "shared-lib", "1.0.0"))
    );
    Path extensionJarPath = createJarWithBootInfLibraryContainingConflictingPomCoordinates(
      Files.createTempFile("seed4j-extension-", ".jar"),
      "extension-shaded.jar",
      new LibraryWithPomCoordinates("ignored-1.jar", "com.acme", "shared-lib", "1.0.0"),
      new LibraryWithPomCoordinates("ignored-2.jar", "org.other", "other-lib", "2.0.0")
    );

    assertThatThrownBy(() -> new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("extension-shaded.jar")
      .hasMessageContaining("conflicting")
      .hasMessageContaining("1.0.0")
      .hasMessageContaining("2.0.0");
  }

  @Test
  void shouldFailFastWhenExtensionNestedPomPropertiesIsIncompleteForRenamedLibrary() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-cli-", ".jar"),
      List.of(new LibraryWithPomCoordinates("cli-renamed.jar", "com.acme", "shared-lib", "1.0.0"))
    );
    Path extensionJarPath = createJarWithBootInfLibrariesAndIncompletePomCoordinates(
      Files.createTempFile("seed4j-extension-", ".jar"),
      "extension-shaded.jar",
      "com.acme",
      "shared-lib"
    );

    assertThatThrownBy(() -> new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath))
      .isInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("extension-shaded.jar")
      .hasMessageContaining("groupId")
      .hasMessageContaining("artifactId")
      .hasMessageContaining("version");
  }

  @Test
  void shouldNotAppendExtensionLibraryWhenFileNameDiffersButPomIdentityMatchesCli() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-cli-", ".jar"),
      List.of(new LibraryWithPomCoordinates("cli-renamed.jar", "com.acme", "shared-lib", "1.0.0"))
    );
    Path extensionJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of(new LibraryWithPomCoordinates("extension-shaded.jar", "com.acme", "shared-lib", "1.0.0"))
    );
    String expectedLoaderPath = overlayClassesPath.toString();

    String loaderPath = new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);

    assertThat(loaderPath).isEqualTo(expectedLoaderPath);
  }

  @Test
  void shouldTreatUppercaseJarExtensionAsRuntimeLibraryEntryWhenComputingMissingLibraries() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibraries(Files.createTempFile("seed4j-cli-", ".jar"), List.of("shared-lib-1.0.0.jar"));
    Path extensionJarPath = createJarWithBootInfLibraries(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of("shared-lib-1.0.0.jar", "missing-lib-2.0.0.JAR")
    );
    String expectedLoaderPath = expectedLoaderPathFor(overlayClassesPath, extensionJarPath, "missing-lib-2.0.0.JAR");

    String loaderPath = new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);

    assertThat(loaderPath).isEqualTo(expectedLoaderPath);
  }

  @Test
  void shouldResolveUsingFileNamesWhenCliNestedLibraryMetadataIsUnreadable() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithUnreadableNestedBootInfLibraries(
      Files.createTempFile("seed4j-cli-", ".jar"),
      List.of("shared-lib-1.0.0.jar")
    );
    Path extensionJarPath = createJarWithBootInfLibraries(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of("shared-lib-1.0.0.jar")
    );
    String expectedLoaderPath = overlayClassesPath.toString();

    String loaderPath = new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);

    assertThat(loaderPath).isEqualTo(expectedLoaderPath);
  }

  @Test
  void shouldTreatCliLibrariesAsEmptyWhenExecutableJarCannotBeInspected() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = Files.createTempDirectory("seed4j-cli-missing-executable-").resolve("missing-cli.jar");
    Path extensionJarPath = createJarWithBootInfLibraries(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of("shared-lib-1.0.0.jar", "missing-lib-2.0.0.jar")
    );
    String expectedLoaderPath = expectedLoaderPathFor(
      overlayClassesPath,
      extensionJarPath,
      "shared-lib-1.0.0.jar",
      "missing-lib-2.0.0.jar"
    );

    String loaderPath = new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);

    assertThat(loaderPath).isEqualTo(expectedLoaderPath);
  }

  @Test
  void shouldResolveUsingFileNamesWhenExtensionNestedLibraryMetadataIsUnreadable() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibraries(Files.createTempFile("seed4j-cli-", ".jar"), List.of("shared-lib-1.0.0.jar"));
    Path extensionJarPath = createJarWithUnreadableNestedBootInfLibraries(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of("shared-lib-1.0.0.jar")
    );
    String expectedLoaderPath = overlayClassesPath.toString();

    String loaderPath = new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);

    assertThat(loaderPath).isEqualTo(expectedLoaderPath);
  }

  @Test
  void shouldResolveUsingFileNamesWhenExtensionNestedLibraryIsNotAJarStream() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibraries(Files.createTempFile("seed4j-cli-", ".jar"), List.of("shared-lib-1.0.0.jar"));
    Path extensionJarPath = createJarWithBootInfLibrariesAndRawBytes(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of("shared-lib-1.0.0.jar"),
      new byte[] { 1, 2, 3, 4, 5 }
    );
    String expectedLoaderPath = overlayClassesPath.toString();

    String loaderPath = new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);

    assertThat(loaderPath).isEqualTo(expectedLoaderPath);
  }

  @Test
  void shouldLogDebugWithTheExactExtensionLibrariesAddedToLoaderPath() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibraries(Files.createTempFile("seed4j-cli-", ".jar"), List.of("shared-lib-1.0.0.jar"));
    Path extensionJarPath = createJarWithBootInfLibraries(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of("shared-lib-1.0.0.jar", "missing-lib-2.0.0.jar")
    );
    Logger logger = (Logger) LoggerFactory.getLogger(RuntimeExtensionLoaderPathResolver.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);

    try {
      new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list)
      .extracting(ILoggingEvent::getFormattedMessage)
      .anyMatch(message -> message.contains("missing-lib-2.0.0.jar"));
  }

  @Test
  void shouldIgnoreNonPomPropertiesMetadataEntriesAndStillResolveCliCoordinatesFromPomProperties() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibrariesAndPomCoordinatesAndPomXmlMetadata(
      Files.createTempFile("seed4j-cli-", ".jar"),
      List.of(
        new LibraryWithPomCoordinates("jackson-core-2.21.2.jar", "com.fasterxml.jackson.core", "jackson-core", "2.21.2"),
        new LibraryWithPomCoordinates("jackson-core-3.1.2.jar", "tools.jackson.core", "jackson-core", "3.1.2")
      )
    );
    Path extensionJarPath = createJarWithBootInfLibraries(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of("shared-lib-1.0.0.jar", "missing-lib-2.0.0.jar")
    );
    String expectedLoaderPath = expectedLoaderPathFor(
      overlayClassesPath,
      extensionJarPath,
      "shared-lib-1.0.0.jar",
      "missing-lib-2.0.0.jar"
    );

    String loaderPath = new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);

    assertThat(loaderPath).isEqualTo(expectedLoaderPath);
  }

  @Test
  void shouldAppendOnlyMissingExtensionLibrariesAsNestedJarEntriesInLoaderPath() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibraries(Files.createTempFile("seed4j-cli-", ".jar"), List.of("shared-lib-1.0.0.jar"));
    Path extensionJarPath = createJarWithBootInfLibraries(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of("shared-lib-1.0.0.jar", "missing-lib-2.0.0.jar")
    );
    String expectedLoaderPath = expectedLoaderPathFor(overlayClassesPath, extensionJarPath, "missing-lib-2.0.0.jar");

    String loaderPath = new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);

    assertThat(loaderPath).isEqualTo(expectedLoaderPath);
  }

  @Test
  void shouldReturnOnlyOverlayClassesWhenNoExtensionLibraryIsMissingFromCli() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibraries(Files.createTempFile("seed4j-cli-", ".jar"), List.of("shared-lib-1.0.0.jar"));
    Path extensionJarPath = createJarWithBootInfLibraries(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of("shared-lib-1.0.0.jar", "README.txt")
    );
    String expectedLoaderPath = overlayClassesPath.toString();

    String loaderPath = new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);

    assertThat(loaderPath).isEqualTo(expectedLoaderPath);
  }

  @Test
  void shouldNotFailWhenCliHasSameArtifactWithDifferentVersionsFromDifferentGroups() throws IOException {
    Path overlayClassesPath = Files.createTempDirectory("seed4j-cli-overlay-");
    Path executableJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-cli-", ".jar"),
      List.of(
        new LibraryWithPomCoordinates("jackson-core-2.21.2.jar", "com.fasterxml.jackson.core", "jackson-core", "2.21.2"),
        new LibraryWithPomCoordinates("jackson-core-3.1.2.jar", "tools.jackson.core", "jackson-core", "3.1.2")
      )
    );
    Path extensionJarPath = createJarWithBootInfLibraries(
      Files.createTempFile("seed4j-extension-", ".jar"),
      List.of("shared-lib-1.0.0.jar", "missing-lib-2.0.0.jar")
    );
    String expectedLoaderPath = expectedLoaderPathFor(
      overlayClassesPath,
      extensionJarPath,
      "shared-lib-1.0.0.jar",
      "missing-lib-2.0.0.jar"
    );

    String loaderPath = new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, executableJarPath);

    assertThat(loaderPath).isEqualTo(expectedLoaderPath);
  }

  @Test
  void shouldCreateValidNestedLibraryJarsWithPomProperties() throws IOException {
    Path executableJarPath = createJarWithBootInfLibrariesAndPomCoordinates(
      Files.createTempFile("seed4j-cli-", ".jar"),
      List.of(new LibraryWithPomCoordinates("jackson-core-2.21.2.jar", "com.fasterxml.jackson.core", "jackson-core", "2.21.2"))
    );
    Path extractedNestedJar = Files.createTempFile("nested-lib-", ".jar");

    try (JarFile executableJar = new JarFile(executableJarPath.toFile())) {
      JarEntry nestedEntry = executableJar.getJarEntry("BOOT-INF/lib/jackson-core-2.21.2.jar");
      Files.write(extractedNestedJar, executableJar.getInputStream(nestedEntry).readAllBytes());
    }

    try (JarFile nestedJar = new JarFile(extractedNestedJar.toFile())) {
      JarEntry pomPropertiesEntry = nestedJar.getJarEntry("META-INF/maven/com.fasterxml.jackson.core/jackson-core/pom.properties");

      assertThat(pomPropertiesEntry).isNotNull();
    }
  }

  private static Path createJarWithBootInfLibraries(Path jarPath, List<String> libraryFileNames) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/"));
      jarOutputStream.closeEntry();
      for (String libraryFileName : libraryFileNames) {
        jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/" + libraryFileName));
        jarOutputStream.write(new byte[] { 1 });
        jarOutputStream.closeEntry();
      }
    }
    return jarPath;
  }

  private static Path createJarWithUnreadableNestedBootInfLibraries(Path jarPath, List<String> libraryFileNames) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/"));
      jarOutputStream.closeEntry();
      for (String libraryFileName : libraryFileNames) {
        jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/" + libraryFileName));
        jarOutputStream.write(invalidNestedJarBytes());
        jarOutputStream.closeEntry();
      }
    }
    return jarPath;
  }

  private static Path createJarWithBootInfLibrariesAndRawBytes(Path jarPath, List<String> libraryFileNames, byte[] nestedLibraryBytes)
    throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/"));
      jarOutputStream.closeEntry();
      for (String libraryFileName : libraryFileNames) {
        jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/" + libraryFileName));
        jarOutputStream.write(nestedLibraryBytes);
        jarOutputStream.closeEntry();
      }
    }
    return jarPath;
  }

  private static String expectedLoaderPathFor(Path overlayClassesPath, Path extensionJarPath, String missingLibraryFileName) {
    String extensionJarUri = extensionJarPath.toUri().toString();
    return overlayClassesPath + ",jar:" + extensionJarUri + "!/BOOT-INF/lib/" + missingLibraryFileName;
  }

  private static String expectedLoaderPathFor(Path overlayClassesPath, Path extensionJarPath, String firstMissing, String secondMissing) {
    String extensionJarUri = extensionJarPath.toUri().toString();
    return (
      overlayClassesPath
      + ",jar:"
      + extensionJarUri
      + "!/BOOT-INF/lib/"
      + firstMissing
      + ",jar:"
      + extensionJarUri
      + "!/BOOT-INF/lib/"
      + secondMissing
    );
  }

  private static Path createJarWithBootInfLibrariesAndPomCoordinates(Path jarPath, List<LibraryWithPomCoordinates> libraries)
    throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/"));
      jarOutputStream.closeEntry();
      for (LibraryWithPomCoordinates library : libraries) {
        jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/" + library.libraryFileName()));
        jarOutputStream.write(nestedJarWithPomCoordinates(library));
        jarOutputStream.closeEntry();
      }
    }
    return jarPath;
  }

  private static Path createJarWithBootInfLibrariesAndIncompletePomCoordinates(
    Path jarPath,
    String libraryFileName,
    String groupId,
    String artifactId
  ) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/" + libraryFileName));
      jarOutputStream.write(nestedJarWithIncompletePomCoordinates(groupId, artifactId));
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static Path createJarWithBootInfLibraryContainingConflictingPomCoordinates(
    Path jarPath,
    String libraryFileName,
    LibraryWithPomCoordinates firstPomCoordinates,
    LibraryWithPomCoordinates secondPomCoordinates
  ) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/" + libraryFileName));
      jarOutputStream.write(nestedJarWithConflictingPomCoordinates(firstPomCoordinates, secondPomCoordinates));
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static Path createJarWithBootInfLibraryContainingMultiplePomCoordinates(
    Path jarPath,
    String libraryFileName,
    List<LibraryWithPomCoordinates> pomCoordinates
  ) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/" + libraryFileName));
      jarOutputStream.write(nestedJarWithMultiplePomCoordinates(pomCoordinates));
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static Path createJarWithBootInfLibrariesAndPomCoordinatesAndPomXmlMetadata(
    Path jarPath,
    List<LibraryWithPomCoordinates> libraries
  ) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/"));
      jarOutputStream.closeEntry();
      for (LibraryWithPomCoordinates library : libraries) {
        jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/lib/" + library.libraryFileName()));
        jarOutputStream.write(nestedJarWithPomCoordinatesAndPomXmlMetadata(library));
        jarOutputStream.closeEntry();
      }
    }
    return jarPath;
  }

  private static byte[] nestedJarWithPomCoordinates(LibraryWithPomCoordinates library) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      JarOutputStream jarOutputStream = new JarOutputStream(outputStream, manifest)
    ) {
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + library.groupId() + "/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + library.groupId() + "/" + library.artifactId() + "/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + library.groupId() + "/" + library.artifactId() + "/pom.properties"));
      jarOutputStream.write(pomPropertiesBytes(library));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("placeholder.txt"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
      jarOutputStream.finish();
      return outputStream.toByteArray();
    }
  }

  private static byte[] nestedJarWithPomCoordinatesAndPomXmlMetadata(LibraryWithPomCoordinates library) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      JarOutputStream jarOutputStream = new JarOutputStream(outputStream, manifest)
    ) {
      jarOutputStream.putNextEntry(new JarEntry("about.txt"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + library.groupId() + "/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + library.groupId() + "/" + library.artifactId() + "/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + library.groupId() + "/" + library.artifactId() + "/pom.xml"));
      jarOutputStream.write("<project/>".getBytes());
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + library.groupId() + "/" + library.artifactId() + "/pom.properties"));
      jarOutputStream.write(pomPropertiesBytes(library));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("placeholder.txt"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
      jarOutputStream.finish();
      return outputStream.toByteArray();
    }
  }

  private static byte[] nestedJarWithIncompletePomCoordinates(String groupId, String artifactId) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      JarOutputStream jarOutputStream = new JarOutputStream(outputStream, manifest)
    ) {
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + groupId + "/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + groupId + "/" + artifactId + "/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties"));
      jarOutputStream.write(incompletePomPropertiesBytes(groupId, artifactId));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("placeholder.txt"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
      jarOutputStream.finish();
      return outputStream.toByteArray();
    }
  }

  private static byte[] nestedJarWithConflictingPomCoordinates(
    LibraryWithPomCoordinates firstPomCoordinates,
    LibraryWithPomCoordinates secondPomCoordinates
  ) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      JarOutputStream jarOutputStream = new JarOutputStream(outputStream, manifest)
    ) {
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + firstPomCoordinates.groupId() + "/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(
        new JarEntry("META-INF/maven/" + firstPomCoordinates.groupId() + "/" + firstPomCoordinates.artifactId() + "/")
      );
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(
        new JarEntry("META-INF/maven/" + firstPomCoordinates.groupId() + "/" + firstPomCoordinates.artifactId() + "/pom.properties")
      );
      jarOutputStream.write(pomPropertiesBytes(firstPomCoordinates));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + secondPomCoordinates.groupId() + "/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(
        new JarEntry("META-INF/maven/" + secondPomCoordinates.groupId() + "/" + secondPomCoordinates.artifactId() + "/")
      );
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(
        new JarEntry("META-INF/maven/" + secondPomCoordinates.groupId() + "/" + secondPomCoordinates.artifactId() + "/pom.properties")
      );
      jarOutputStream.write(pomPropertiesBytes(secondPomCoordinates));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("placeholder.txt"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
      jarOutputStream.finish();
      return outputStream.toByteArray();
    }
  }

  private static byte[] nestedJarWithMultiplePomCoordinates(List<LibraryWithPomCoordinates> pomCoordinatesList) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      JarOutputStream jarOutputStream = new JarOutputStream(outputStream, manifest)
    ) {
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/"));
      jarOutputStream.closeEntry();

      for (LibraryWithPomCoordinates pomCoordinates : pomCoordinatesList) {
        jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + pomCoordinates.groupId() + "/"));
        jarOutputStream.closeEntry();
        jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/" + pomCoordinates.groupId() + "/" + pomCoordinates.artifactId() + "/"));
        jarOutputStream.closeEntry();
        jarOutputStream.putNextEntry(
          new JarEntry("META-INF/maven/" + pomCoordinates.groupId() + "/" + pomCoordinates.artifactId() + "/pom.properties")
        );
        jarOutputStream.write(pomPropertiesBytes(pomCoordinates));
        jarOutputStream.closeEntry();
      }

      jarOutputStream.putNextEntry(new JarEntry("placeholder.txt"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
      jarOutputStream.finish();
      return outputStream.toByteArray();
    }
  }

  private static byte[] pomPropertiesBytes(LibraryWithPomCoordinates library) throws IOException {
    Properties properties = new Properties();
    properties.setProperty("groupId", library.groupId());
    properties.setProperty("artifactId", library.artifactId());
    properties.setProperty("version", library.version());
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      properties.store(outputStream, null);
      return outputStream.toByteArray();
    }
  }

  private static byte[] incompletePomPropertiesBytes(String groupId, String artifactId) throws IOException {
    Properties properties = new Properties();
    properties.setProperty("groupId", groupId);
    properties.setProperty("artifactId", artifactId);
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      properties.store(outputStream, null);
      return outputStream.toByteArray();
    }
  }

  private static byte[] invalidNestedJarBytes() throws IOException {
    byte[] nestedJarBytes = nestedJarWithStoredEntries();
    byte[] firstEntryBytes = "SEED4J-INVALID-CRC-PAYLOAD".getBytes(StandardCharsets.UTF_8);
    int firstEntryBytesIndex = indexOfBytes(nestedJarBytes, firstEntryBytes);
    nestedJarBytes[firstEntryBytesIndex + firstEntryBytes.length / 2] = 0;
    return nestedJarBytes;
  }

  private static byte[] nestedJarWithStoredEntries() throws IOException {
    byte[] firstEntryBytes = "SEED4J-INVALID-CRC-PAYLOAD".getBytes(StandardCharsets.UTF_8);
    byte[] secondEntryBytes = "SEED4J-STORED-ENTRY".getBytes(StandardCharsets.UTF_8);

    try (
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      JarOutputStream jarOutputStream = new JarOutputStream(outputStream)
    ) {
      JarEntry firstEntry = storedJarEntry("about.txt", firstEntryBytes);
      jarOutputStream.putNextEntry(firstEntry);
      jarOutputStream.write(firstEntryBytes);
      jarOutputStream.closeEntry();

      JarEntry secondEntry = storedJarEntry("placeholder.txt", secondEntryBytes);
      jarOutputStream.putNextEntry(secondEntry);
      jarOutputStream.write(secondEntryBytes);
      jarOutputStream.closeEntry();

      jarOutputStream.finish();
      return outputStream.toByteArray();
    }
  }

  private static JarEntry storedJarEntry(String entryName, byte[] entryBytes) {
    CRC32 crc32 = new CRC32();
    crc32.update(entryBytes);

    JarEntry jarEntry = new JarEntry(entryName);
    jarEntry.setMethod(ZipEntry.STORED);
    jarEntry.setSize(entryBytes.length);
    jarEntry.setCompressedSize(entryBytes.length);
    jarEntry.setCrc(crc32.getValue());
    return jarEntry;
  }

  private static int indexOfBytes(byte[] input, byte[] sequence) {
    for (int index = 0; index <= input.length - sequence.length; index++) {
      boolean sequenceMatches = true;
      for (int offset = 0; offset < sequence.length; offset++) {
        if (input[index + offset] != sequence[offset]) {
          sequenceMatches = false;
          break;
        }
      }

      if (sequenceMatches) {
        return index;
      }
    }
    throw new IllegalStateException("Could not locate nested entry bytes in fixture");
  }

  private record LibraryWithPomCoordinates(String libraryFileName, String groupId, String artifactId, String version) {}
}
