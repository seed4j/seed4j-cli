package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class RuntimeExtensionInstaller {

  private static final Path CONFIG_PATH = Path.of(".config", "seed4j-cli", "config.yml");
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
    Path configPath = userHome.resolve(CONFIG_PATH);
    Map<Object, Object> currentConfiguration = validateInstallRequest(request);
    boolean runtimeReplaced = activeRuntimePresent(runtimeExtensionConfiguration);

    try {
      installRuntimeArtifacts(request, runtimeExtensionConfiguration);
      ensureExtensionMode(configPath, currentConfiguration);
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

  private Map<Object, Object> validateInstallRequest(RuntimeExtensionInstallRequest request) {
    runtimeExtensionJarLayoutValidator.validate(request.extensionJarPath());
    return runtimeModeConfigReader.configuration(userHome);
  }

  private static boolean activeRuntimePresent(RuntimeExtensionConfiguration runtimeExtensionConfiguration) {
    return (Files.exists(runtimeExtensionConfiguration.jarPath()) || Files.exists(runtimeExtensionConfiguration.metadataPath()));
  }

  private static void installRuntimeArtifacts(
    RuntimeExtensionInstallRequest request,
    RuntimeExtensionConfiguration runtimeExtensionConfiguration
  ) throws IOException {
    Path runtimeDirectoryPath = runtimeExtensionConfiguration.jarPath().getParent();
    Files.createDirectories(runtimeDirectoryPath);
    replacePathWithSource(request.extensionJarPath(), runtimeExtensionConfiguration.jarPath());
    replacePathWithContent(metadataContent(request), runtimeExtensionConfiguration.metadataPath());
  }

  private static void ensureExtensionMode(Path configPath, Map<Object, Object> currentConfiguration) throws IOException {
    Files.createDirectories(configPath.getParent());
    replacePathWithContent(extensionModeConfiguration(currentConfiguration), configPath);
  }

  private static String metadataContent(RuntimeExtensionInstallRequest request) {
    return """
    distribution:
      id: %s
      version: %s
    """.formatted(request.distributionId(), request.distributionVersion());
  }

  private static String extensionModeConfiguration(Map<Object, Object> currentConfiguration) {
    Map<Object, Object> configuration = new LinkedHashMap<>(currentConfiguration);
    Map<Object, Object> seed4j = nestedMap(configuration, "seed4j");
    Map<Object, Object> runtime = nestedMap(seed4j, "runtime");
    runtime.put("mode", "extension");

    return yaml().dump(configuration);
  }

  private static Map<Object, Object> nestedMap(Map<Object, Object> source, String key) {
    Object existingValue = source.get(key);
    if (existingValue instanceof Map<?, ?> existingMap) {
      Map<Object, Object> nestedValue = new LinkedHashMap<>(existingMap);
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

  @ExcludeFromGeneratedCodeCoverage(reason = "Cache publication race branch depends on filesystem concurrency timing")
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
