package com.seed4j.cli.command.infrastructure.primary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import picocli.CommandLine;

abstract class ExtensionRuntimeCommandsTest {

  protected static final String DISTRIBUTION_ID = "company-extension";
  protected static final String DISTRIBUTION_VERSION = "1.0.0";
  protected static final Path USER_HOME = temporaryDirectory();

  @Autowired
  private Seed4JCommandsFactory commandsFactory;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("user.home", USER_HOME::toString);
  }

  @BeforeEach
  void cleanUserHomeConfiguration() throws IOException {
    deleteRecursively(USER_HOME.resolve(".config/seed4j-cli"));
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (Files.notExists(path)) {
      return;
    }

    try (Stream<Path> paths = Files.walk(path)) {
      for (Path currentPath : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(currentPath);
      }
    }
  }

  protected CommandLine commandLine() {
    return new CommandLine(commandsFactory.buildCommandSpec());
  }

  protected String[] installArguments(Path extensionJarPath) {
    return new String[] {
      "extension",
      "install",
      extensionJarPath.toString(),
      "--distribution-id",
      DISTRIBUTION_ID,
      "--distribution-version",
      DISTRIBUTION_VERSION,
    };
  }

  protected ExtensionRuntimePaths runtimePaths() {
    Path runtimeDirectory = USER_HOME.resolve(".config/seed4j-cli/runtime/active");
    return new ExtensionRuntimePaths(
      USER_HOME.resolve(".config/seed4j-cli/config.yml"),
      runtimeDirectory.resolve("extension.jar"),
      runtimeDirectory.resolve("metadata.yml")
    );
  }

  protected ActiveRuntimeArtifacts installActiveRuntime(ExtensionRuntimePaths runtimePaths) throws IOException {
    Files.createDirectories(runtimePaths.runtimeJarPath().getParent());
    createFatJar(runtimePaths.runtimeJarPath());
    writeRuntimeMetadata(runtimePaths.metadataPath(), DISTRIBUTION_ID, DISTRIBUTION_VERSION);
    return new ActiveRuntimeArtifacts(Files.readAllBytes(runtimePaths.runtimeJarPath()), Files.readString(runtimePaths.metadataPath()));
  }

  protected void installRuntimeWithoutMetadata(ExtensionRuntimePaths runtimePaths) throws IOException {
    Files.createDirectories(runtimePaths.runtimeJarPath().getParent());
    createFatJar(runtimePaths.runtimeJarPath());
  }

  protected String writeRuntimeMode(Path configPath, String mode) throws IOException {
    Files.createDirectories(configPath.getParent());
    String config = """
    seed4j:
      runtime:
        mode: %s
    """.formatted(mode);
    Files.writeString(configPath, config);
    return config;
  }

  protected void writeRuntimeMetadata(Path metadataPath, String distributionId, String distributionVersion) throws IOException {
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: %s
        version: %s
      """.formatted(distributionId, distributionVersion)
    );
  }

  protected Path createFatJar(Path jarPath) throws IOException {
    return writeFatJar(jarPath, List.of());
  }

  private Path writeFatJar(Path jarPath, List<TestJarEntry> additionalEntries) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      writeJarEntry(jarOutputStream, new TestJarEntry("BOOT-INF/", new byte[] {}));
      writeJarEntry(jarOutputStream, new TestJarEntry("BOOT-INF/classes/", new byte[] {}));
      for (TestJarEntry additionalEntry : additionalEntries) {
        writeJarEntry(jarOutputStream, additionalEntry);
      }
    }

    return jarPath;
  }

  private void writeJarEntry(JarOutputStream jarOutputStream, TestJarEntry entry) throws IOException {
    jarOutputStream.putNextEntry(new JarEntry(entry.name()));
    jarOutputStream.write(entry.content());
    jarOutputStream.closeEntry();
  }

  protected Path createFatJarWithClass(Path jarPath, String entryName, byte[] entryContent) throws IOException {
    return writeFatJar(jarPath, List.of(new TestJarEntry(entryName, entryContent)));
  }

  private static Path temporaryDirectory() {
    try {
      return Files.createTempDirectory("seed4j-cli-spring-context-");
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  protected record ExtensionRuntimePaths(Path configPath, Path runtimeJarPath, Path metadataPath) {}

  protected record ActiveRuntimeArtifacts(byte[] jarContent, String metadataContent) {}

  private record TestJarEntry(String name, byte[] content) {}
}
