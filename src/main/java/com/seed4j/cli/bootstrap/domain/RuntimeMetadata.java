package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.Yaml;

record RuntimeMetadata(String distributionId, String distributionVersion, Optional<String> compatibilityMinCliVersion) {
  static RuntimeMetadata read(Path metadataPath) {
    try {
      Object loadedMetadata = new Yaml().load(Files.newInputStream(metadataPath));
      if (!(loadedMetadata instanceof Map<?, ?> metadata)) {
        throw invalidMetadata(metadataPath);
      }

      Map<?, ?> distribution = mapValue(metadata, "distribution", "distribution", metadataPath);
      Optional<Map<?, ?>> compatibility = optionalMapValue(metadata, "compatibility", "compatibility", metadataPath);

      return new RuntimeMetadata(
        stringValue(distribution, "id", "distribution.id", metadataPath),
        stringValue(distribution, "version", "distribution.version", metadataPath),
        compatibility.flatMap(values -> optionalStringValue(values, "min-cli-version", "compatibility.min-cli-version", metadataPath))
      );
    } catch (IOException | RuntimeException e) {
      if (e instanceof InvalidRuntimeConfigurationException runtimeConfigurationException) {
        throw runtimeConfigurationException;
      }

      throw invalidMetadata(metadataPath);
    }
  }

  private static Map<?, ?> mapValue(Map<?, ?> source, String key, String fieldName, Path metadataPath) {
    Object value = source.get(key);
    if (!(value instanceof Map<?, ?> mapValue)) {
      throw invalidMetadata(fieldName, metadataPath);
    }

    return mapValue;
  }

  private static Optional<Map<?, ?>> optionalMapValue(Map<?, ?> source, String key, String fieldName, Path metadataPath) {
    if (!source.containsKey(key)) {
      return Optional.empty();
    }

    Object value = source.get(key);
    if (!(value instanceof Map<?, ?> mapValue)) {
      throw invalidMetadata(fieldName, metadataPath);
    }

    return Optional.of(mapValue);
  }

  private static String stringValue(Map<?, ?> source, String key, String fieldName, Path metadataPath) {
    Object value = source.get(key);
    if (!(value instanceof String stringValue) || stringValue.isBlank()) {
      throw invalidMetadata(fieldName, metadataPath);
    }

    return stringValue;
  }

  private static Optional<String> optionalStringValue(Map<?, ?> source, String key, String fieldName, Path metadataPath) {
    if (!source.containsKey(key)) {
      return Optional.empty();
    }

    Object value = source.get(key);
    if (!(value instanceof String stringValue) || stringValue.isBlank()) {
      throw invalidMetadata(fieldName, metadataPath);
    }

    return Optional.of(stringValue);
  }

  private static InvalidRuntimeConfigurationException invalidMetadata(Path metadataPath) {
    return new InvalidRuntimeConfigurationException("Invalid runtime metadata file: " + metadataPath);
  }

  private static InvalidRuntimeConfigurationException invalidMetadata(String fieldName, Path metadataPath) {
    return new InvalidRuntimeConfigurationException("Invalid " + fieldName + " in runtime metadata file: " + metadataPath);
  }
}
