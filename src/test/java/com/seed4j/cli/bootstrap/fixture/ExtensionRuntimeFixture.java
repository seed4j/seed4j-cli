package com.seed4j.cli.bootstrap.fixture;

import com.mycompany.seed4j.extension.runtime.fixture.RuntimeExtensionApplySharedApplication;
import com.mycompany.seed4j.extension.runtime.fixture.RuntimeExtensionCommonSourceApplication;
import com.mycompany.seed4j.extension.runtime.fixture.RuntimeExtensionListApplication;
import com.mycompany.seed4j.extension.runtime.list.MyCompanyRuntimeExtensionApplication;
import com.mycompany.seed4j.extension.runtime.list.MyCompanyRuntimeExtensionListOnlyApplicationService;
import com.mycompany.seed4j.extension.runtime.list.MyCompanyRuntimeExtensionListOnlyModuleConfiguration;
import com.mycompany.seed4j.extension.runtime.list.MyCompanyRuntimeExtensionListOnlyModuleFactory;
import com.mycompany.seed4j.extension.runtime.list.MyCompanyRuntimeExtensionListOnlyModuleSlug;
import com.mycompany.seed4j.extension.runtime.main.apply.RuntimeExtensionApplySharedContextApplicationService;
import com.mycompany.seed4j.extension.runtime.main.apply.RuntimeExtensionApplySharedContextModuleConfiguration;
import com.mycompany.seed4j.extension.runtime.main.apply.RuntimeExtensionApplySharedContextModuleFactory;
import com.mycompany.seed4j.extension.runtime.main.apply.RuntimeExtensionApplySharedContextModuleSlug;
import com.mycompany.seed4j.extension.runtime.main.apply.RuntimeExtensionCommonSourceNodePackagesVersionsReader;
import com.mycompany.seed4j.extension.runtime.main.list.RuntimeExtensionListOnlyApplicationService;
import com.mycompany.seed4j.extension.runtime.main.list.RuntimeExtensionListOnlyModuleConfiguration;
import com.mycompany.seed4j.extension.runtime.main.list.RuntimeExtensionListOnlyModuleFactory;
import com.mycompany.seed4j.extension.runtime.main.list.RuntimeExtensionListOnlyModuleSlug;
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

public final class ExtensionRuntimeFixture {

  private static final String EXTENSION_MODE_CONFIG = """
    seed4j:
      runtime:
        mode: extension
    """;
  private static final String CONFIG_FILE_LOCATION = ".config/seed4j-cli/config.yml";
  private static final String RUNTIME_DIRECTORY_LOCATION = ".config/seed4j-cli/runtime/active";
  private static final String METADATA_RESOURCE_LOCATION = "runtime/extension/metadata.yml";
  private static final String BOOT_INF_DIRECTORY = "BOOT-INF/";
  private static final String BOOT_INF_CLASSES_DIRECTORY = "BOOT-INF/classes/";
  private static final String BOOT_INF_CONFIG_DIRECTORY = "BOOT-INF/classes/config/";
  private static final String BOOT_INF_DEPENDENCIES_DIRECTORY = "BOOT-INF/classes/generator/dependencies/";
  private static final String BOOT_INF_COMMON_DEPENDENCIES_DIRECTORY = "BOOT-INF/classes/generator/dependencies/common/";
  private static final String BOOT_INF_GENERATOR_DIRECTORY = "BOOT-INF/classes/generator/";
  private static final String BOOT_INF_PRETTIER_DIRECTORY = "BOOT-INF/classes/generator/prettier/";
  private static final String EXTENSION_APPLICATION_YML_ENTRY = "BOOT-INF/classes/config/application.yml";
  private static final String EXTENSION_LOGBACK_ENTRY = "BOOT-INF/classes/logback-spring.xml";
  private static final String EXTENSION_COMMON_DEPENDENCIES_ENTRY = "BOOT-INF/classes/generator/dependencies/common/package.json";
  private static final String EXTENSION_PRETTIER_TEMPLATE_ENTRY = "BOOT-INF/classes/generator/prettier/.prettierrc.mustache";
  private static final String EXTENSION_COMMON_DEPENDENCIES_OVERRIDE = """
    {
      "dependencies": {
        "@prettier/plugin-xml": "3.4.2",
        "husky": "9.1.7",
        "lint-staged": "16.2.7",
        "node": "24.12.0",
        "npm": "11.7.0",
        "prettier": "3.6.2",
        "prettier-plugin-gherkin": "3.1.3",
        "prettier-plugin-java": "2.7.7",
        "prettier-plugin-organize-imports": "4.3.0",
        "prettier-plugin-packagejson": "2.5.20"
      }
    }
    """;
  private static final String EXTENSION_PRETTIER_TEMPLATE_OVERRIDE = """
    # seed4j-extension-template-override

    printWidth: 140
    singleQuote: true
    tabWidth: {{indentSize}}
    useTabs: false
    endOfLine: '{{endOfLine}}'
    """;
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
  private static final String EXTENSION_APPLICATION_WITH_HIDDEN_RESOURCES_YML = """
    seed4j:
      hidden-resources:
        slugs:
          - gradle-java
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
  private static final String EXTENSION_LOGBACK_CONFIGURATION_WITH_SCAN = """
    <?xml version="1.0" encoding="UTF-8" ?>
    <!DOCTYPE configuration>
    <configuration scan="true">
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
  private static final String EXTENSION_START_CLASS = RuntimeExtensionListApplication.class.getName();
  private static final String COMMON_SOURCE_EXTENSION_START_CLASS = RuntimeExtensionCommonSourceApplication.class.getName();
  private static final String APPLY_SHARED_EXTENSION_START_CLASS = RuntimeExtensionApplySharedApplication.class.getName();
  private static final String CUSTOM_PACKAGE_EXTENSION_START_CLASS = MyCompanyRuntimeExtensionApplication.class.getName();
  private static final List<Class<?>> LIST_EXTENSION_APPLICATION_CLASSES = List.of(RuntimeExtensionListApplication.class);
  private static final List<Class<?>> COMMON_SOURCE_EXTENSION_APPLICATION_CLASSES = List.of(RuntimeExtensionCommonSourceApplication.class);
  private static final List<Class<?>> APPLY_SHARED_EXTENSION_APPLICATION_CLASSES = List.of(RuntimeExtensionApplySharedApplication.class);
  private static final List<Class<?>> LIST_EXTENSION_MODULE_CLASSES = List.of(
    RuntimeExtensionListOnlyModuleSlug.class,
    RuntimeExtensionListOnlyModuleFactory.class,
    RuntimeExtensionListOnlyApplicationService.class,
    RuntimeExtensionListOnlyModuleConfiguration.class
  );
  private static final List<Class<?>> CUSTOM_PACKAGE_LIST_EXTENSION_MODULE_CLASSES = List.of(
    MyCompanyRuntimeExtensionApplication.class,
    MyCompanyRuntimeExtensionListOnlyModuleSlug.class,
    MyCompanyRuntimeExtensionListOnlyModuleFactory.class,
    MyCompanyRuntimeExtensionListOnlyApplicationService.class,
    MyCompanyRuntimeExtensionListOnlyModuleConfiguration.class
  );
  private static final List<Class<?>> APPLY_COMMON_SOURCE_OVERRIDE_EXTENSION_CLASSES = List.of(
    RuntimeExtensionCommonSourceNodePackagesVersionsReader.class
  );
  private static final List<Class<?>> APPLY_SHARED_RUNTIME_EXTENSION_MODULE_CLASSES = List.of(
    RuntimeExtensionApplySharedContextModuleSlug.class,
    RuntimeExtensionApplySharedContextModuleFactory.class,
    RuntimeExtensionApplySharedContextApplicationService.class,
    RuntimeExtensionApplySharedContextModuleConfiguration.class
  );

  private ExtensionRuntimeFixture() {}

  public static ExtensionRuntimeFixturePaths install(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createMinimalJar);
  }

  public static ExtensionRuntimeFixturePaths installWithListExtensionModule(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createListExtensionModuleJar);
  }

  public static ExtensionRuntimeFixturePaths installWithApplyCommonSourceOverrideExtensionModule(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createListExtensionModuleJarWithCommonSourceOverride);
  }

  public static ExtensionRuntimeFixturePaths installWithApplyExtensionModuleUsingSharedRuntimeOverrides(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createListExtensionModuleJarWithApplySharedRuntimeOverrides);
  }

  public static ExtensionRuntimeFixturePaths installWithListExtensionModuleAndLoggingOverrides(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createListExtensionModuleJarWithLoggingOverrides);
  }

  public static ExtensionRuntimeFixturePaths installWithListExtensionModuleAndRegressionOverrides(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createListExtensionModuleJarWithRegressionOverrides);
  }

  public static ExtensionRuntimeFixturePaths installWithListExtensionModuleAndHiddenResourcesOverrides(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createListExtensionModuleJarWithHiddenResourcesOverrides);
  }

  public static ExtensionRuntimeFixturePaths installWithFlatJar(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createFlatJar);
  }

  public static ExtensionRuntimeFixturePaths installWithCustomPackageListExtensionModule(Path userHome) throws IOException {
    return install(userHome, ExtensionRuntimeFixture::createCustomPackageListExtensionModuleJar);
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
    return createListExtensionModuleJar(jarPath);
  }

  public static Path createListExtensionModuleJar(Path jarPath) throws IOException {
    Manifest manifest = manifestWithStartClass();
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      addedEntries.add(JarFile.MANIFEST_NAME);
      addDirectoryEntry(jarOutputStream, BOOT_INF_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY);
      addedEntries.add(BOOT_INF_DIRECTORY);
      addedEntries.add(BOOT_INF_CLASSES_DIRECTORY);
      for (Class<?> applicationClass : LIST_EXTENSION_APPLICATION_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, applicationClass, addedEntries);
      }
      for (Class<?> moduleClass : LIST_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, moduleClass, addedEntries);
      }
    }
    return jarPath;
  }

  public static Path createListExtensionModuleJarWithCommonSourceOverride(Path jarPath) throws IOException {
    Manifest manifest = manifestWithStartClass(COMMON_SOURCE_EXTENSION_START_CLASS);
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      addedEntries.add(JarFile.MANIFEST_NAME);
      addDirectoryEntry(jarOutputStream, BOOT_INF_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_GENERATOR_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_DEPENDENCIES_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_COMMON_DEPENDENCIES_DIRECTORY);
      addedEntries.add(BOOT_INF_DIRECTORY);
      addedEntries.add(BOOT_INF_CLASSES_DIRECTORY);
      addedEntries.add(BOOT_INF_GENERATOR_DIRECTORY);
      addedEntries.add(BOOT_INF_DEPENDENCIES_DIRECTORY);
      addedEntries.add(BOOT_INF_COMMON_DEPENDENCIES_DIRECTORY);
      for (Class<?> applicationClass : COMMON_SOURCE_EXTENSION_APPLICATION_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, applicationClass, addedEntries);
      }
      for (Class<?> moduleClass : LIST_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, moduleClass, addedEntries);
      }
      for (Class<?> overrideClass : APPLY_COMMON_SOURCE_OVERRIDE_EXTENSION_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, overrideClass, addedEntries);
      }
      addTextEntry(jarOutputStream, EXTENSION_COMMON_DEPENDENCIES_ENTRY, EXTENSION_COMMON_DEPENDENCIES_OVERRIDE);
    }
    return jarPath;
  }

  public static Path createListExtensionModuleJarWithApplySharedRuntimeOverrides(Path jarPath) throws IOException {
    Manifest manifest = manifestWithStartClass(APPLY_SHARED_EXTENSION_START_CLASS);
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      addedEntries.add(JarFile.MANIFEST_NAME);
      addDirectoryEntry(jarOutputStream, BOOT_INF_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_GENERATOR_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_DEPENDENCIES_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_COMMON_DEPENDENCIES_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_PRETTIER_DIRECTORY);
      addedEntries.add(BOOT_INF_DIRECTORY);
      addedEntries.add(BOOT_INF_CLASSES_DIRECTORY);
      addedEntries.add(BOOT_INF_GENERATOR_DIRECTORY);
      addedEntries.add(BOOT_INF_DEPENDENCIES_DIRECTORY);
      addedEntries.add(BOOT_INF_COMMON_DEPENDENCIES_DIRECTORY);
      addedEntries.add(BOOT_INF_PRETTIER_DIRECTORY);
      for (Class<?> applicationClass : APPLY_SHARED_EXTENSION_APPLICATION_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, applicationClass, addedEntries);
      }
      for (Class<?> moduleClass : LIST_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, moduleClass, addedEntries);
      }
      for (Class<?> moduleClass : APPLY_SHARED_RUNTIME_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, moduleClass, addedEntries);
      }
      for (Class<?> overrideClass : APPLY_COMMON_SOURCE_OVERRIDE_EXTENSION_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, overrideClass, addedEntries);
      }
      addTextEntry(jarOutputStream, EXTENSION_COMMON_DEPENDENCIES_ENTRY, EXTENSION_COMMON_DEPENDENCIES_OVERRIDE);
      addTextEntry(jarOutputStream, EXTENSION_PRETTIER_TEMPLATE_ENTRY, EXTENSION_PRETTIER_TEMPLATE_OVERRIDE);
    }
    return jarPath;
  }

  public static Path createCustomPackageListExtensionModuleJar(Path jarPath) throws IOException {
    Manifest manifest = manifestWithStartClass(CUSTOM_PACKAGE_EXTENSION_START_CLASS);
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      addedEntries.add(JarFile.MANIFEST_NAME);
      addDirectoryEntry(jarOutputStream, BOOT_INF_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY);
      addedEntries.add(BOOT_INF_DIRECTORY);
      addedEntries.add(BOOT_INF_CLASSES_DIRECTORY);
      for (Class<?> moduleClass : CUSTOM_PACKAGE_LIST_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, moduleClass, addedEntries);
      }
    }
    return jarPath;
  }

  public static Path createListExtensionModuleJarWithLoggingOverrides(Path jarPath) throws IOException {
    Manifest manifest = manifestWithStartClass();
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      addedEntries.add(JarFile.MANIFEST_NAME);
      addDirectoryEntry(jarOutputStream, BOOT_INF_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CONFIG_DIRECTORY);
      addedEntries.add(BOOT_INF_DIRECTORY);
      addedEntries.add(BOOT_INF_CLASSES_DIRECTORY);
      addedEntries.add(BOOT_INF_CONFIG_DIRECTORY);
      for (Class<?> applicationClass : LIST_EXTENSION_APPLICATION_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, applicationClass, addedEntries);
      }
      for (Class<?> moduleClass : LIST_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, moduleClass, addedEntries);
      }
      addTextEntry(jarOutputStream, EXTENSION_APPLICATION_YML_ENTRY, EXTENSION_APPLICATION_YML);
      addTextEntry(jarOutputStream, EXTENSION_LOGBACK_ENTRY, EXTENSION_LOGBACK_CONFIGURATION);
    }
    return jarPath;
  }

  public static Path createListExtensionModuleJarWithRegressionOverrides(Path jarPath) throws IOException {
    Manifest manifest = manifestWithStartClass();
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      addedEntries.add(JarFile.MANIFEST_NAME);
      addDirectoryEntry(jarOutputStream, BOOT_INF_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CONFIG_DIRECTORY);
      addedEntries.add(BOOT_INF_DIRECTORY);
      addedEntries.add(BOOT_INF_CLASSES_DIRECTORY);
      addedEntries.add(BOOT_INF_CONFIG_DIRECTORY);
      for (Class<?> applicationClass : LIST_EXTENSION_APPLICATION_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, applicationClass, addedEntries);
      }
      for (Class<?> moduleClass : LIST_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, moduleClass, addedEntries);
      }
      addTextEntry(jarOutputStream, EXTENSION_APPLICATION_YML_ENTRY, EXTENSION_APPLICATION_YML);
      addTextEntry(jarOutputStream, EXTENSION_LOGBACK_ENTRY, EXTENSION_LOGBACK_CONFIGURATION_WITH_SCAN);
    }
    return jarPath;
  }

  public static Path createListExtensionModuleJarWithHiddenResourcesOverrides(Path jarPath) throws IOException {
    Manifest manifest = manifestWithStartClass();
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Set<String> addedEntries = new HashSet<>();
      addedEntries.add(JarFile.MANIFEST_NAME);
      addDirectoryEntry(jarOutputStream, BOOT_INF_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY);
      addDirectoryEntry(jarOutputStream, BOOT_INF_CONFIG_DIRECTORY);
      addedEntries.add(BOOT_INF_DIRECTORY);
      addedEntries.add(BOOT_INF_CLASSES_DIRECTORY);
      addedEntries.add(BOOT_INF_CONFIG_DIRECTORY);
      for (Class<?> applicationClass : LIST_EXTENSION_APPLICATION_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, applicationClass, addedEntries);
      }
      for (Class<?> moduleClass : LIST_EXTENSION_MODULE_CLASSES) {
        addClassAndNestedClasses(jarOutputStream, BOOT_INF_CLASSES_DIRECTORY, moduleClass, addedEntries);
      }
      addTextEntry(jarOutputStream, EXTENSION_APPLICATION_YML_ENTRY, EXTENSION_APPLICATION_WITH_HIDDEN_RESOURCES_YML);
    }
    return jarPath;
  }

  public static Path createFlatJar(Path jarPath) throws IOException {
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

  private static Manifest manifestWithStartClass() {
    return manifestWithStartClass(EXTENSION_START_CLASS);
  }

  private static Manifest manifestWithStartClass(String startClass) {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Start-Class", startClass);
    return manifest;
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

  public record ExtensionRuntimeFixturePaths(Path configFilePath, Path metadataPath, Path extensionJarPath) {}
}
