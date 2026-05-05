package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
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
}
