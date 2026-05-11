package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionLoaderPathResolverTest {

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

  private record LibraryWithPomCoordinates(String libraryFileName, String groupId, String artifactId, String version) {}
}
