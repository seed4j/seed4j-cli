package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

public class RuntimeExtensionInstaller {

  private final Path userHome;
  private final RuntimeModeConfigReader runtimeModeConfigReader;
  private final RuntimeExtensionJarLayoutValidator runtimeExtensionJarLayoutValidator;

  public RuntimeExtensionInstaller(Path userHome) {
    this.userHome = userHome;
    this.runtimeModeConfigReader = new RuntimeModeConfigReader();
    this.runtimeExtensionJarLayoutValidator = new RuntimeExtensionJarLayoutValidator();
  }

  public RuntimeExtensionInstallResult install(RuntimeExtensionInstallRequest request) {
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = RuntimeExtensionConfiguration.withDefaultPaths(userHome);
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    runtimeExtensionJarLayoutValidator.validate(request.extensionJarPath());
    if (Files.exists(configPath)) {
      runtimeModeConfigReader.runtimeMode(userHome);
    }

    boolean runtimeReplaced =
      Files.exists(runtimeExtensionConfiguration.jarPath()) || Files.exists(runtimeExtensionConfiguration.metadataPath());
    Path runtimeDirectoryPath = runtimeExtensionConfiguration.jarPath().getParent();

    try {
      Files.createDirectories(runtimeDirectoryPath);
      replacePathWithSource(request.extensionJarPath(), runtimeExtensionConfiguration.jarPath());
      replacePathWithContent(metadataContent(request), runtimeExtensionConfiguration.metadataPath());

      Files.createDirectories(configPath.getParent());
      replacePathWithContent(extensionModeConfiguration(configPath), configPath);
    } catch (InvalidRuntimeConfigurationException invalidRuntimeConfigurationException) {
      throw invalidRuntimeConfigurationException;
    } catch (YAMLException yamlException) {
      throw new InvalidRuntimeConfigurationException("Could not read ~/.config/seed4j-cli/config.yml.");
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException("Could not install runtime extension: " + ioException.getMessage());
    }

    return new RuntimeExtensionInstallResult(
      runtimeExtensionConfiguration.jarPath(),
      runtimeExtensionConfiguration.metadataPath(),
      configPath,
      runtimeReplaced
    );
  }

  private static String metadataContent(RuntimeExtensionInstallRequest request) {
    return """
    distribution:
      id: %s
      version: %s
    """.formatted(request.distributionId(), request.distributionVersion());
  }

  private static String extensionModeConfiguration(Path configPath) throws IOException {
    Map<Object, Object> configuration = loadConfiguration(configPath);
    Map<Object, Object> seed4j = nestedMap(configuration, "seed4j");
    Map<Object, Object> runtime = nestedMap(seed4j, "runtime");
    runtime.put("mode", "extension");

    return yaml().dump(configuration);
  }

  private static Map<Object, Object> loadConfiguration(Path configPath) throws IOException {
    if (!Files.exists(configPath)) {
      return new LinkedHashMap<>();
    }

    Object loadedConfiguration;
    try (InputStream configInputStream = Files.newInputStream(configPath)) {
      loadedConfiguration = new Yaml().load(configInputStream);
    }
    if (!(loadedConfiguration instanceof Map<?, ?> loadedConfigurationMap)) {
      throw new InvalidRuntimeConfigurationException("Could not read ~/.config/seed4j-cli/config.yml.");
    }

    return linkedHashMap(loadedConfigurationMap);
  }

  private static Map<Object, Object> nestedMap(Map<Object, Object> source, String key) {
    Object existingValue = source.get(key);
    if (existingValue instanceof Map<?, ?> existingMap) {
      Map<Object, Object> nestedValue = linkedHashMap(existingMap);
      source.put(key, nestedValue);
      return nestedValue;
    }

    Map<Object, Object> createdMap = new LinkedHashMap<>();
    source.put(key, createdMap);
    return createdMap;
  }

  private static Yaml yaml() {
    DumperOptions dumperOptions = new DumperOptions();
    dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    dumperOptions.setPrettyFlow(true);

    return new Yaml(dumperOptions);
  }

  private static Map<Object, Object> linkedHashMap(Map<?, ?> sourceMap) {
    Map<Object, Object> copy = new LinkedHashMap<>();
    sourceMap.forEach(copy::put);
    return copy;
  }

  private static void replacePathWithSource(Path sourcePath, Path targetPath) throws IOException {
    Path temporaryPath = temporaryPath(targetPath);
    try {
      Files.copy(sourcePath, temporaryPath, StandardCopyOption.REPLACE_EXISTING);
      moveReplacing(temporaryPath, targetPath);
    } catch (IOException ioException) {
      Files.deleteIfExists(temporaryPath);
      throw ioException;
    }
  }

  private static void replacePathWithContent(String content, Path targetPath) throws IOException {
    Path temporaryPath = temporaryPath(targetPath);
    try {
      Files.writeString(temporaryPath, content);
      moveReplacing(temporaryPath, targetPath);
    } catch (IOException ioException) {
      Files.deleteIfExists(temporaryPath);
      throw ioException;
    }
  }

  private static void moveReplacing(Path sourcePath, Path targetPath) throws IOException {
    try {
      Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException _) {
      Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static Path temporaryPath(Path targetPath) {
    String temporaryFileName = "." + targetPath.getFileName() + ".tmp-" + UUID.randomUUID();
    return targetPath.getParent().resolve(temporaryFileName);
  }
}
