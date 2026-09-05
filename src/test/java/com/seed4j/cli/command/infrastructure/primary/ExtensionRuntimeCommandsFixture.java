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
import picocli.CommandLine;

final class ExtensionRuntimeCommandsFixture {

  private static final String DISTRIBUTION_ID = "company-extension";
  private static final String DISTRIBUTION_VERSION = "1.0.0";

  private final Path userHome;
  private final Seed4JCommandsFactory commandsFactory;

  ExtensionRuntimeCommandsFixture(Path userHome, Seed4JCommandsFactory commandsFactory) {
    this.userHome = userHome;
    this.commandsFactory = commandsFactory;
  }

  static Path temporaryUserHome() {
    try {
      return Files.createTempDirectory("seed4j-cli-spring-context-");
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  void cleanUserHomeConfiguration() throws IOException {
    deleteRecursively(userHome.resolve(".config/seed4j-cli"));
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

  CommandLine commandLine() {
    return new CommandLine(commandsFactory.buildCommandSpec());
  }

  String[] installArguments(Path extensionJarPath) {
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

  ExtensionRuntimePaths runtimePaths() {
    Path runtimeDirectory = userHome.resolve(".config/seed4j-cli/runtime/active");
    return new ExtensionRuntimePaths(
      userHome.resolve(".config/seed4j-cli/config.yml"),
      runtimeDirectory.resolve("extension.jar"),
      runtimeDirectory.resolve("metadata.yml")
    );
  }

  ActiveRuntimeArtifacts installActiveRuntime(ExtensionRuntimePaths runtimePaths) throws IOException {
    Files.createDirectories(runtimePaths.runtimeJarPath().getParent());
    createFatJar(runtimePaths.runtimeJarPath());
    writeRuntimeMetadata(runtimePaths.metadataPath(), DISTRIBUTION_ID, DISTRIBUTION_VERSION);
    return new ActiveRuntimeArtifacts(Files.readAllBytes(runtimePaths.runtimeJarPath()), Files.readString(runtimePaths.metadataPath()));
  }

  void installRuntimeWithoutMetadata(ExtensionRuntimePaths runtimePaths) throws IOException {
    Files.createDirectories(runtimePaths.runtimeJarPath().getParent());
    createFatJar(runtimePaths.runtimeJarPath());
  }

  String writeRuntimeMode(Path configPath, String mode) throws IOException {
    Files.createDirectories(configPath.getParent());
    String config = """
    seed4j:
      runtime:
        mode: %s
    """.formatted(mode);
    Files.writeString(configPath, config);
    return config;
  }

  void writeRuntimeMetadata(Path metadataPath, String distributionId, String distributionVersion) throws IOException {
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: %s
        version: %s
      """.formatted(distributionId, distributionVersion)
    );
  }

  Path createFatJar(Path jarPath) throws IOException {
    return writeFatJar(jarPath, List.of());
  }

  Path createFatJarWithClass(Path jarPath, String entryName, byte[] entryContent) throws IOException {
    return writeFatJar(jarPath, List.of(new TestJarEntry(entryName, entryContent)));
  }

  private static Path writeFatJar(Path jarPath, List<TestJarEntry> additionalEntries) throws IOException {
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

  private static void writeJarEntry(JarOutputStream jarOutputStream, TestJarEntry entry) throws IOException {
    jarOutputStream.putNextEntry(new JarEntry(entry.name()));
    jarOutputStream.write(entry.content());
    jarOutputStream.closeEntry();
  }

  record ExtensionRuntimePaths(Path configPath, Path runtimeJarPath, Path metadataPath) {}

  record ActiveRuntimeArtifacts(byte[] jarContent, String metadataContent) {}

  private record TestJarEntry(String name, byte[] content) {}
}
