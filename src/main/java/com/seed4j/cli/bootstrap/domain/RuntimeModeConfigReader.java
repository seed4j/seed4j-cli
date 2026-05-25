package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

class RuntimeModeConfigReader {

  private static final String SEED4J_KEY = "seed4j";

  RuntimeMode runtimeMode(Path userHome) {
    Map<Object, Object> configuration = configuration(userHome);
    return runtimeMode(configuration);
  }

  Map<Object, Object> configuration(Path userHome) {
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    if (!Files.exists(configPath)) {
      return new LinkedHashMap<>();
    }

    try (InputStream configInputStream = Files.newInputStream(configPath)) {
      Object loadedConfiguration = new Yaml().load(configInputStream);
      if (!(loadedConfiguration instanceof Map<?, ?> loadedConfigurationMap)) {
        throw new InvalidRuntimeConfigurationException("Invalid ~/.config/seed4j-cli/config.yml: YAML root must be an object.");
      }

      if (loadedConfigurationMap.containsKey(SEED4J_KEY) && !(loadedConfigurationMap.get(SEED4J_KEY) instanceof Map<?, ?>)) {
        throw new InvalidRuntimeConfigurationException("Invalid ~/.config/seed4j-cli/config.yml: seed4j must be an object.");
      }

      if (loadedConfigurationMap.get(SEED4J_KEY) instanceof Map<?, ?> seed4j && seed4j.get("runtime") instanceof Map<?, ?> runtime) {
        if (runtime.containsKey("mode") && !(runtime.get("mode") instanceof String)) {
          throw new InvalidRuntimeConfigurationException(
            "Invalid ~/.config/seed4j-cli/config.yml: seed4j.runtime.mode must be a string. Valid values: standard, extension."
          );
        }
      }

      return new LinkedHashMap<>(loadedConfigurationMap);
    } catch (IOException | YAMLException _) {
      throw new InvalidRuntimeConfigurationException("Could not read ~/.config/seed4j-cli/config.yml.");
    }
  }

  private RuntimeMode runtimeMode(Map<Object, Object> configuration) {
    if (!(configuration.get(SEED4J_KEY) instanceof Map<?, ?> seed4j)) {
      return RuntimeMode.STANDARD;
    }
    if (!(seed4j.get("runtime") instanceof Map<?, ?> runtime)) {
      return RuntimeMode.STANDARD;
    }
    if (!(runtime.get("mode") instanceof String mode)) {
      return RuntimeMode.STANDARD;
    }

    String normalizedMode = mode.trim().toUpperCase();
    return runtimeMode(mode, normalizedMode);
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
}
