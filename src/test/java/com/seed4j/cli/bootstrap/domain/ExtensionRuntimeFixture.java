package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyApplicationService;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleConfiguration;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleFactory;
import com.seed4j.cli.bootstrap.domain.runtimeextension.list.RuntimeExtensionListOnlyModuleSlug;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private static final String BOOT_INF_DIRECTORY = "BOOT-INF/";
  private static final String BOOT_INF_CLASSES_DIRECTORY = "BOOT-INF/classes/";
  private static final String BOOT_INF_CONFIG_DIRECTORY = "BOOT-INF/classes/config/";
  private static final String EXTENSION_APPLICATION_YML_ENTRY = "BOOT-INF/classes/config/application.yml";
  private static final String EXTENSION_LOGBACK_ENTRY = "BOOT-INF/classes/logback-spring.xml";
  private static final String EXTENSION_APPLICATION_YML = """
    logging:
      level:
        root: INFO
      pattern:
        console: "[EXT-APPLICATION-OVERRIDE] %msg%n"
    spring:
      main:
        log-startup-info: true
    """;
  private static final String EXTENSION_LOGBACK_CONFIGURATION = """
    <?xml version="1.0" encoding="UTF-8" ?>
    <!DOCTYPE configuration>
    <configuration scan="false">
      <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
          <pattern>[EXT-LOGBACK-OVERRIDE] %msg%n</pattern>
        </encoder>
      </appender>

      <root level="INFO">
        <appender-ref ref="CONSOLE" />
      </root>
    </configuration>
    """;
  private static final String FLAT_CLASS_ENTRY = "com/seed4j/cli/runtime/FlatExtensionMarker.class";
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

  static ExtensionRuntimeFixturePaths installWithListExtensionModuleAndLoggingOverrides(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createListExtensionModuleJarWithLoggingOverrides);
  }

  static ExtensionRuntimeFixturePaths installWithFlatJar(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createFlatJar);
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
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      addDirectoryEntry(jarOutputStream, BOOT_INF_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY);
    }
    return jarPath;
  }

  static Path createListExtensionModuleJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      addedEntries.add(JarFile.MANIFEST_NAME);
      addDirectoryEntry(jarOutputStream, BOOT_INF_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY);
      addedEntries.add(BOOT_INF_DIRECTORY);
      addedEntries.add(BOOT_INF_CLASSES_DIRECTORY);
      for (Class<?> moduleClass : LIST_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, moduleClass, addedEntries);
      }
    }
    return jarPath;
  }

  static Path createListExtensionModuleJarWithLoggingOverrides(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      addedEntries.add(JarFile.MANIFEST_NAME);
      addDirectoryEntry(jarOutputStream, BOOT_INF_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CONFIG_DIRECTORY);
      addedEntries.add(BOOT_INF_DIRECTORY);
      addedEntries.add(BOOT_INF_CLASSES_DIRECTORY);
      addedEntries.add(BOOT_INF_CONFIG_DIRECTORY);
      for (Class<?> moduleClass : LIST_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, moduleClass, addedEntries);
      }
      addTextEntry(jarOutputStream, EXTENSION_APPLICATION_YML_ENTRY, EXTENSION_APPLICATION_YML);
      addTextEntry(jarOutputStream, EXTENSION_LOGBACK_ENTRY, EXTENSION_LOGBACK_CONFIGURATION);
    }
    return jarPath;
  }

  static Path createFlatJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      addPackageDirectories(jarOutputStream, FLAT_CLASS_ENTRY, new HashSet<>());
      jarOutputStream.putNextEntry(new JarEntry(FLAT_CLASS_ENTRY));
      jarOutputStream.write(new byte[] { 0 });
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static void addClassAndNestedClasses(
    JarOutputStream jarOutputStream,
    String classEntryPrefix,
    Class<?> classToAdd,
    Set<String> addedEntries
  ) throws IOException {
    addClassEntry(jarOutputStream, classEntryPrefix, classToAdd, addedEntries);
    for (Class<?> nestedClass : classToAdd.getDeclaredClasses()) {
      addClassAndNestedClasses(jarOutputStream, classEntryPrefix, nestedClass, addedEntries);
    }
  }

  private static void addClassEntry(JarOutputStream jarOutputStream, String classEntryPrefix, Class<?> classToAdd, Set<String> addedEntries)
    throws IOException {
    String classFileEntryName = classToAdd.getName().replace('.', '/') + ".class";
    String classEntryName = classEntryPrefix + classFileEntryName;
    addPackageDirectories(jarOutputStream, classEntryName, addedEntries);
    if (!addedEntries.add(classEntryName)) {
      return;
    }

    try (InputStream classInputStream = classInputStream(classToAdd, classFileEntryName)) {
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

  private static void addDirectoryEntry(JarOutputStream jarOutputStream, String directoryEntry) throws IOException {
    jarOutputStream.putNextEntry(new JarEntry(directoryEntry));
    jarOutputStream.closeEntry();
  }

  private static void addTextEntry(JarOutputStream jarOutputStream, String entryName, String content) throws IOException {
    jarOutputStream.putNextEntry(new JarEntry(entryName));
    jarOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
    jarOutputStream.closeEntry();
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
