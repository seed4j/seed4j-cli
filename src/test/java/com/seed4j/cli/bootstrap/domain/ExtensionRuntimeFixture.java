package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyApplicationService;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleConfiguration;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleFactory;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleSlug;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

final class ExtensionRuntimeFixture {

  private static final String EXTENSION_MODE_CONFIG = """
    seed4j:
      runtime:
        mode: extension
    """;
  private static final String CONFIG_FILE_LOCATION = ".config/seed4j-cli.yml";
  private static final String RUNTIME_DIRECTORY_LOCATION = ".config/seed4j-cli/runtime/active";
  private static final String METADATA_RESOURCE_LOCATION = "runtime/extension/metadata.yml";
  private static final List<Class<?>> LIST_EXTENSION_MODULE_CLASSES = List.of(
    RuntimeExtensionListOnlyModuleSlug.class,
    RuntimeExtensionListOnlyModuleFactory.class,
    RuntimeExtensionListOnlyApplicationService.class,
    RuntimeExtensionListOnlyModuleConfiguration.class
  );

  private ExtensionRuntimeFixture() {}

  static ExtensionRuntimeFixturePaths install(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createMinimalJar);
  }

  static ExtensionRuntimeFixturePaths installWithListExtensionModule(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createListExtensionModuleJar);
  }

  private static ExtensionRuntimeFixturePaths install(Path userHome, ExtensionJarFactory extensionJarFactory) throws IOException {
    Path runtimeDirectory = userHome.resolve(RUNTIME_DIRECTORY_LOCATION);
    Path configFilePath = userHome.resolve(CONFIG_FILE_LOCATION);
    Files.createDirectories(runtimeDirectory);
    Files.createDirectories(configFilePath.getParent());
    Files.writeString(configFilePath, EXTENSION_MODE_CONFIG);

    Path extensionJarPath = extensionJarFactory.create(runtimeDirectory.resolve("extension.jar"));
    Path metadataPath = runtimeDirectory.resolve("metadata.yml");
    copyMetadataFixture(metadataPath);

    return new ExtensionRuntimeFixturePaths(configFilePath, metadataPath, extensionJarPath);
  }

  static Path createMinimalJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      // Keep the extension archive minimal while preserving a valid JAR structure.
    }
    return jarPath;
  }

  static Path createListExtensionModuleJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      for (Class<?> moduleClass : LIST_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, moduleClass, addedEntries);
      }
    }
    return jarPath;
  }

  private static void addClassAndNestedClasses(JarOutputStream jarOutputStream, Class<?> classToAdd, Set<String> addedEntries)
    throws IOException {
    addClassEntry(jarOutputStream, classToAdd, addedEntries);
    for (Class<?> nestedClass : classToAdd.getDeclaredClasses()) {
      addClassAndNestedClasses(jarOutputStream, nestedClass, addedEntries);
    }
  }

  private static void addClassEntry(JarOutputStream jarOutputStream, Class<?> classToAdd, Set<String> addedEntries) throws IOException {
    String classEntryName = classToAdd.getName().replace('.', '/') + ".class";
    addPackageDirectories(jarOutputStream, classEntryName, addedEntries);
    if (!addedEntries.add(classEntryName)) {
      return;
    }

    try (InputStream classInputStream = classInputStream(classToAdd, classEntryName)) {
      if (classInputStream == null) {
        throw new IllegalStateException("Could not find class bytes for fixture module class: " + classToAdd.getName());
      }
      jarOutputStream.putNextEntry(new JarEntry(classEntryName));
      classInputStream.transferTo(jarOutputStream);
      jarOutputStream.closeEntry();
    }
  }

  private static void addPackageDirectories(JarOutputStream jarOutputStream, String classEntryName, Set<String> addedEntries)
    throws IOException {
    String[] classEntryPathSegments = classEntryName.split("/");
    StringBuilder directory = new StringBuilder();
    for (int i = 0; i < classEntryPathSegments.length - 1; i++) {
      directory.append(classEntryPathSegments[i]).append('/');
      String directoryEntry = directory.toString();
      if (addedEntries.add(directoryEntry)) {
        jarOutputStream.putNextEntry(new JarEntry(directoryEntry));
        jarOutputStream.closeEntry();
      }
    }
  }

  private static InputStream classInputStream(Class<?> classToAdd, String classEntryName) {
    return Optional.ofNullable(classToAdd.getClassLoader())
      .map(classLoader -> classLoader.getResourceAsStream(classEntryName))
      .orElseGet(() -> ClassLoader.getSystemResourceAsStream(classEntryName));
  }

  private static void copyMetadataFixture(Path metadataPath) throws IOException {
    try (InputStream metadataInputStream = ExtensionRuntimeFixture.class.getClassLoader().getResourceAsStream(METADATA_RESOURCE_LOCATION)) {
      if (metadataInputStream == null) {
        throw new IllegalStateException("Missing runtime metadata fixture resource: " + METADATA_RESOURCE_LOCATION);
      }

      Files.copy(metadataInputStream, metadataPath);
    }
  }

  private interface ExtensionJarFactory {
    Path create(Path jarPath) throws IOException;
  }

  record ExtensionRuntimeFixturePaths(Path configFilePath, Path metadataPath, Path extensionJarPath) {}
}
