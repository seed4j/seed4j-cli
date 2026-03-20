package com.seed4j.cli.bootstrap.domain;

import com.seed4j.Seed4JApp;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyApplicationService;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleConfiguration;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleFactory;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleSlug;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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
  private static final String BASE_JAR_APPLICATION_CONFIG_ENTRY = "config/application.yml";
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
    new JarOutputStream(Files.newOutputStream(jarPath), manifest).close();
    return jarPath;
  }

  static Path createListExtensionModuleJar(Path jarPath) throws IOException {
    Path seed4jDependencyJarPath = resolveSeed4jDependencyJarPath();
    Manifest manifest = manifestFrom(seed4jDependencyJarPath);
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      addedEntries.add(JarFile.MANIFEST_NAME);
      copyJarEntries(seed4jDependencyJarPath, jarOutputStream, addedEntries);
      for (Class<?> moduleClass : LIST_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, moduleClass, addedEntries);
      }
    }
    return jarPath;
  }

  private static Path resolveSeed4jDependencyJarPath() {
    CodeSource seed4jCodeSource = Seed4JApp.class.getProtectionDomain().getCodeSource();
    if (seed4jCodeSource == null || seed4jCodeSource.getLocation() == null) {
      throw new IllegalStateException("Could not resolve seed4j dependency JAR location from com.seed4j.Seed4JApp code source.");
    }

    try {
      Path codeSourcePath = Path.of(seed4jCodeSource.getLocation().toURI());
      if (!Files.isRegularFile(codeSourcePath) || !codeSourcePath.getFileName().toString().endsWith(".jar")) {
        throw new IllegalStateException("Resolved seed4j dependency location is not a JAR file: " + codeSourcePath);
      }

      return codeSourcePath;
    } catch (URISyntaxException exception) {
      throw new IllegalStateException("Could not resolve seed4j dependency JAR path from code source URI.", exception);
    }
  }

  private static Manifest manifestFrom(Path sourceJarPath) throws IOException {
    try (JarFile sourceJar = new JarFile(sourceJarPath.toFile())) {
      Manifest sourceManifest = sourceJar.getManifest();
      if (sourceManifest != null) {
        return sourceManifest;
      }
    }

    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    return manifest;
  }

  private static void copyJarEntries(Path sourceJarPath, JarOutputStream targetJarOutputStream, Set<String> addedEntries)
    throws IOException {
    try (JarFile sourceJar = new JarFile(sourceJarPath.toFile())) {
      Enumeration<JarEntry> sourceEntries = sourceJar.entries();
      while (sourceEntries.hasMoreElements()) {
        JarEntry sourceEntry = sourceEntries.nextElement();
        copyJarEntry(sourceJar, sourceEntry, targetJarOutputStream, addedEntries);
      }
    }
  }

  private static void copyJarEntry(JarFile sourceJar, JarEntry sourceEntry, JarOutputStream targetJarOutputStream, Set<String> addedEntries)
    throws IOException {
    String sourceEntryName = sourceEntry.getName();
    if (BASE_JAR_APPLICATION_CONFIG_ENTRY.equals(sourceEntryName)) {
      return;
    }

    if (!addedEntries.add(sourceEntryName)) {
      return;
    }

    targetJarOutputStream.putNextEntry(new JarEntry(sourceEntryName));
    if (!sourceEntry.isDirectory()) {
      try (InputStream sourceEntryInputStream = sourceJar.getInputStream(sourceEntry)) {
        sourceEntryInputStream.transferTo(targetJarOutputStream);
      }
    }
    targetJarOutputStream.closeEntry();
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
