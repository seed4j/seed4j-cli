package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationDocument;
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

final class RuntimeModeConfigurationWriter {

  private static final String SEED4J_KEY = "seed4j";
  private static final String RUNTIME_KEY = "runtime";
  private static final String MODE_KEY = "mode";

  private RuntimeModeConfigurationWriter() {}

  static void writeMode(Path configPath, RuntimeModeConfigurationDocument currentConfiguration, RuntimeMode mode) throws IOException {
    Files.createDirectories(configPath.getParent());
    replacePathWithContent(configurationWithMode(currentConfiguration, mode), configPath);
  }

  private static String configurationWithMode(RuntimeModeConfigurationDocument currentConfiguration, RuntimeMode mode) {
    Map<Object, Object> configuration = new LinkedHashMap<>(currentConfiguration.configuration());
    Map<Object, Object> seed4j = nestedMap(configuration, SEED4J_KEY);
    Map<Object, Object> runtime = nestedMap(seed4j, RUNTIME_KEY);
    runtime.put(MODE_KEY, mode.name().toLowerCase());

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
