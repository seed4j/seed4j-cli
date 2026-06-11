package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationDocument;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

class RuntimeModeConfigReader {

  private static final String SEED4J_KEY = "seed4j";
  private static final String RUNTIME_KEY = "runtime";
  private static final String MODE_KEY = "mode";

  RuntimeMode runtimeMode(Path configPath) {
    RuntimeModeConfigurationDocument configurationDocument = configuration(configPath);
    return runtimeMode(configurationDocument.configuration());
  }

  RuntimeModeConfigurationDocument configuration(Path configPath) {
    if (!Files.exists(configPath)) {
      return new RuntimeModeConfigurationDocument(new LinkedHashMap<>());
    }

    try (InputStream configInputStream = Files.newInputStream(configPath)) {
      Object loadedConfiguration = new Yaml().load(configInputStream);
      if (!(loadedConfiguration instanceof Map<?, ?> loadedConfigurationMap)) {
        throw new InvalidRuntimeConfigurationException("Invalid ~/.config/seed4j-cli/config.yml: YAML root must be an object.");
      }

      validateSeed4jConfiguration(loadedConfigurationMap);
      validateRuntimeModeConfiguration(loadedConfigurationMap);

      return new RuntimeModeConfigurationDocument(new LinkedHashMap<>(loadedConfigurationMap));
    } catch (IOException | YAMLException exception) {
      throw InvalidRuntimeConfigurationException.technicalError("Could not read ~/.config/seed4j-cli/config.yml.", exception);
    }
  }

  private void validateSeed4jConfiguration(Map<?, ?> loadedConfigurationMap) {
    if (loadedConfigurationMap.containsKey(SEED4J_KEY) && seed4jSection(loadedConfigurationMap).isEmpty()) {
      throw new InvalidRuntimeConfigurationException("Invalid ~/.config/seed4j-cli/config.yml: seed4j must be an object.");
    }
  }

  private void validateRuntimeModeConfiguration(Map<?, ?> loadedConfigurationMap) {
    RuntimeModeEntry runtimeModeEntry = runtimeModeEntry(loadedConfigurationMap);
    if (runtimeModeEntry.invalidType()) {
      throw new InvalidRuntimeConfigurationException(
        "Invalid ~/.config/seed4j-cli/config.yml: seed4j.runtime.mode must be a string. Valid values: standard, extension."
      );
    }
  }

  private RuntimeMode runtimeMode(Map<Object, Object> configuration) {
    return runtimeModeEntry(configuration)
      .valueAsString()
      .map(configuredMode -> runtimeMode(configuredMode, configuredMode.trim().toUpperCase()))
      .orElse(RuntimeMode.STANDARD);
  }

  private RuntimeMode runtimeMode(String mode, String normalizedMode) {
    try {
      return RuntimeMode.valueOf(normalizedMode);
    } catch (IllegalArgumentException _) {
      throw new InvalidRuntimeConfigurationException(
        "Invalid seed4j.runtime.mode '%s'. Valid values: standard, extension.".formatted(mode)
      );
    }
  }

  private RuntimeModeEntry runtimeModeEntry(Map<?, ?> configuration) {
    return runtimeSection(configuration)
      .filter(runtime -> runtime.containsKey(MODE_KEY))
      .map(runtime -> RuntimeModeEntry.present(runtime.get(MODE_KEY)))
      .orElseGet(RuntimeModeEntry::missing);
  }

  private Optional<Map<?, ?>> runtimeSection(Map<?, ?> configuration) {
    return seed4jSection(configuration).flatMap(seed4j -> mapSection(seed4j, RUNTIME_KEY));
  }

  private Optional<Map<?, ?>> seed4jSection(Map<?, ?> configuration) {
    return mapSection(configuration, SEED4J_KEY);
  }

  private Optional<Map<?, ?>> mapSection(Map<?, ?> configuration, String key) {
    Object section = configuration.get(key);
    if (!(section instanceof Map<?, ?> sectionMap)) {
      return Optional.empty();
    }

    return Optional.of(sectionMap);
  }

  private record RuntimeModeEntry(boolean present, Object value) {
    static RuntimeModeEntry missing() {
      return new RuntimeModeEntry(false, null);
    }

    static RuntimeModeEntry present(Object value) {
      return new RuntimeModeEntry(true, value);
    }

    boolean invalidType() {
      return present && !(value instanceof String);
    }

    Optional<String> valueAsString() {
      if (value instanceof String configuredMode) {
        return Optional.of(configuredMode);
      }

      return Optional.empty();
    }
  }
}
